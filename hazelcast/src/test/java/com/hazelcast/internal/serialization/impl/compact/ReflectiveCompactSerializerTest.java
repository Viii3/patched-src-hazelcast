/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.internal.serialization.impl.compact;

import com.hazelcast.config.ClassFilter;
import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.internal.serialization.impl.ExtraReflectiveCompactSerializationRestrictions;
import com.hazelcast.internal.serialization.impl.TestAccessors;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class ReflectiveCompactSerializerTest {

    public ReflectiveCompactSerializerTest()
            throws IOException {
    }

    public static class ExampleDto {
        int field;
        String text;

        public ExampleDto(int field, String text) {
            this.field = field;
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ExampleDto that = (ExampleDto) o;
            return field == that.field && Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field, text);
        }
    }

    InMemorySchemaService schemaService = new InMemorySchemaService();
    ExampleDto obj = new ExampleDto(1, "aaa");
    byte[] serializedObj = serialize(obj, null, null);

    @Test
    public void writeWithoutRestriction() {
        assertThatNoException().isThrownBy(() -> serialize(obj, null, null));
    }

    @Test
    public void writeOtherClassBlocked() {
        assertThatNoException().isThrownBy(() -> serialize(obj, otherClassFilter(), null));
    }

    @Test
    public void writeClassBlockedThrows() {
        assertThatThrownBy(() -> serialize(obj, thisClassFilter(), null)).isInstanceOf(
                ReflectiveCompactSerializationUnsupportedException.class);
    }

    @Test
    public void writeOtherClassAllowedThrows() {
        assertThatThrownBy(() -> serialize(obj, null, otherClassFilter())).isInstanceOf(
                ReflectiveCompactSerializationUnsupportedException.class);
    }

    @Test
    public void writeClassBlockedAndAllowedThrows() {
        assertThatThrownBy(() -> serialize(obj, thisClassFilter(), thisClassFilter())).isInstanceOf(
                ReflectiveCompactSerializationUnsupportedException.class);
    }

    @Test
    public void writeClassAllowed() {
        assertThatNoException().isThrownBy(() -> serialize(obj, null, thisClassFilter()));
    }

    @Test
    public void readWithoutBlocklist()
            throws IOException {
        assertThat(deserialize(serializedObj, null, null)).isEqualTo(obj);
    }

    @Test
    public void readOtherClassBlocked()
            throws IOException {
        assertThat(deserialize(serializedObj, otherClassFilter(), null)).isEqualTo(obj);
    }

    @Test
    public void readClassBlockedReturnsGenericRecord()
            throws IOException {
        assertThat(deserialize(serializedObj, thisClassFilter(), null)).isInstanceOf(DeserializedGenericRecord.class);
    }

    @Test
    public void readClassBlockedAndAllowedReturnsGenericRecord()
            throws IOException {
        assertThat(deserialize(serializedObj, thisClassFilter(), thisClassFilter())).isInstanceOf(
                DeserializedGenericRecord.class);
    }

    @Test
    public void readOtherClassBlockedAndThisClassAllowed()
            throws IOException {
        assertThat(deserialize(serializedObj, otherClassFilter(), thisClassFilter())).isEqualTo(obj);
    }

    @Test
    public void readOtherClassAllowed()
            throws IOException {
        assertThat(deserialize(serializedObj, null, otherClassFilter())).isInstanceOf(DeserializedGenericRecord.class);
    }

    @Test
    public void readEmptyAllowList()
            throws IOException {
        assertThat(deserialize(serializedObj, null, new ClassFilter())).isInstanceOf(DeserializedGenericRecord.class);
    }

    @Test
    public void readClassAllowed()
            throws IOException {
        assertThat(deserialize(serializedObj, null, thisClassFilter())).isEqualTo(obj);
    }

    private ClassFilter otherClassFilter() {
        return new ClassFilter().addClasses("com.hazelcast.some.OtherClass");
    }

    private ClassFilter thisClassFilter() {
        return new ClassFilter().addClasses(ExampleDto.class.getName());
    }

    private byte[] serialize(Object obj, ClassFilter blockList, ClassFilter allowList)
            throws IOException {
        CompactStreamSerializer compactStreamSerializer = getCompactStreamSerializer(blockList, allowList);
        ObjectDataOutput out = TestAccessors.createByteArrayObjectDataOutput(null);
        compactStreamSerializer.write(out, obj);
        return out.toByteArray();
    }

    private Object deserialize(byte[] data, ClassFilter blockList, ClassFilter allowList)
            throws IOException {
        ObjectDataInput in = TestAccessors.createByteArrayObjectDataInput(null, data);
        return getCompactStreamSerializer(blockList, allowList).read(in);
    }

    private CompactStreamSerializer getCompactStreamSerializer(ClassFilter blockList, ClassFilter allowList) {
        return new CompactStreamSerializer(cls -> CompactStreamSerializerAdapter.class, new CompactSerializationConfig(), null,
                schemaService, null, new ExtraReflectiveCompactSerializationRestrictions(allowList, blockList, null));
    }
}
