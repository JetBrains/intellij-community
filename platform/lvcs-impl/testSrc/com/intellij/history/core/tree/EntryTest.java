/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.core.tree;

import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.Paths;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class EntryTest extends LocalHistoryTestCase {
  @Test
  public void testPathEquality() {
    Entry e = new MyEntry() {
      @Override
      public String getPath() {
        return "path";
      }
    };
    assertTrue(e.pathEquals("path"));
    assertFalse(e.pathEquals("bla-bla-bla"));

    Paths.setCaseSensitive(true);
    assertFalse(e.pathEquals("PATH"));

    Paths.setCaseSensitive(false);
    assertTrue(e.pathEquals("PATH"));
  }

  @Test
  public void testSerializationWithFileIdEnabled() throws Exception {
    Registry.get("lvcs.store.entry.file.id").setValue(true);
    try {
      MyEntry original = new MyEntry("testFile.txt", 123);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(baos);
      original.write(out);

      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      DataInputStream in = new DataInputStream(bais);
      MyEntry deserialized = new MyEntry(in);

      assertEqual(original, deserialized);
    }
    finally {
      Registry.get("lvcs.store.entry.file.id").resetToDefault();
    }
  }

  @Test
  public void testSerializationWithFileIdDisabled() throws Exception {
    Registry.get("lvcs.store.entry.file.id").setValue(false);
    try {
      MyEntry original = new MyEntry("testFile.txt", 123);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(baos);
      original.write(out);

      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      DataInputStream in = new DataInputStream(bais);
      MyEntry deserialized = new MyEntry(in);

      assertEqual(original, deserialized);
    }
    finally {
      Registry.get("lvcs.store.entry.file.id").resetToDefault();
    }
  }

  @Test
  public void testBackwardCompatibility() throws Exception {
    try {
      // Write in old format (file id disabled)
      Registry.get("lvcs.store.entry.file.id").setValue(false);
      MyEntry original = new MyEntry("testFile.txt", 123);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(baos);
      original.write(out);

      // Read with new format enabled (should still work)
      Registry.get("lvcs.store.entry.file.id").setValue(true);
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      DataInputStream in = new DataInputStream(bais);
      MyEntry deserialized = new MyEntry(in);

      assertEqual(original, deserialized);
    }
    finally {
      Registry.get("lvcs.store.entry.file.id").resetToDefault();
    }
  }

  @Test
  public void testForwardCompatibility() throws Exception {
    try {
      // Write in new format
      Registry.get("lvcs.store.entry.file.id").setValue(true);
      MyEntry original = new MyEntry("testFile.txt", 123);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(baos);
      original.write(out);

      // Read with old format enabled (should still work)
      Registry.get("lvcs.store.entry.file.id").setValue(false);
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      DataInputStream in = new DataInputStream(bais);
      MyEntry deserialized = new MyEntry(in);

      assertEqual(original, deserialized);
    }
    finally {
      Registry.get("lvcs.store.entry.file.id").resetToDefault();
    }
  }

  private static void assertEqual(MyEntry original, MyEntry deserialized) {
    assertEquals(original.getName(), deserialized.getName());
    assertEquals(original.getNameId(), deserialized.getNameId());
    assertEquals(original.getNameHash(), deserialized.getNameHash());
    assertEquals(original.getAdditionalData(), deserialized.getAdditionalData());
  }

  private static class MyEntry extends Entry {
    private final long additionalData;

    MyEntry() {
      super((String)null);
      additionalData = -1;
    }

    MyEntry(String name, long additionalData) {
      super(name);
      this.additionalData = additionalData;
    }

    MyEntry(DataInputStream in) throws Exception {
      super(in);
      this.additionalData = in.readLong();
    }

    @Override
    public void write(DataOutput out) throws IOException {
      super.write(out);
      out.writeLong(additionalData);
    }

    private long getAdditionalData() {
      return additionalData;
    }

    @Override
    public long getTimestamp() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Entry copy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void collectDifferencesWith(@NotNull Entry e, @NotNull BiConsumer<Entry, Entry> consumer) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void collectCreatedDifferences(@NotNull BiConsumer<Entry, Entry> consumer) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void collectDeletedDifferences(@NotNull BiConsumer<Entry, Entry> consumer) {
      throw new UnsupportedOperationException();
    }
  }
}
