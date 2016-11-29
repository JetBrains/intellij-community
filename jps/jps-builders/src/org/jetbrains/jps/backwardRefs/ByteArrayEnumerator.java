/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.ObjectUtils;
import com.intellij.util.io.*;
import com.sun.tools.javac.util.Convert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;
import java.util.Arrays;

public class ByteArrayEnumerator extends PersistentEnumeratorDelegate<byte[]> {
  @NotNull
  private final CachingEnumerator<byte[]> myCache;

  public ByteArrayEnumerator(@NotNull final File file) throws IOException {
    super(file, ByteSequenceDataExternalizer.INSTANCE, 1024 * 4, null);
    myCache = new CachingEnumerator<byte[]>(new DataEnumerator<byte[]>() {
      @Override
      public int enumerate(@Nullable byte[] value) throws IOException {
        return ByteArrayEnumerator.super.enumerate(value);
      }

      @Nullable
      @Override
      public byte[] valueOf(int idx) throws IOException {
        return ByteArrayEnumerator.super.valueOf(idx);
      }
    }, ByteSequenceDataExternalizer.INSTANCE);
  }

  @Override
  public synchronized int enumerate(@Nullable byte[] value) {
    try {
      return myCache.enumerate(value);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Nullable
  @Override
  public synchronized byte[] valueOf(int idx) throws IOException {
    return myCache.valueOf(idx);
  }

  @Override
  public synchronized void close() throws IOException {
    super.close();
    myCache.close();
  }

  @NotNull
  public String getName(int idx) {
    try {
      return Convert.utf2string(ObjectUtils.notNull(valueOf(idx)));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static class ByteSequenceDataExternalizer implements KeyDescriptor<byte[]>, DifferentSerializableBytesImplyNonEqualityPolicy {
    private static final ByteSequenceDataExternalizer INSTANCE = new ByteSequenceDataExternalizer();

    @Override
    public void save(@NotNull DataOutput out, byte[] value) throws IOException {
      out.writeInt(value.length);
      out.write(value);
    }

    @Override
    public byte[] read(@NotNull DataInput in) throws IOException {
      final int len = in.readInt();
      final byte[] buf = new byte[len];
      in.readFully(buf);
      return buf;
    }

    @Override
    public int getHashCode(byte[] value) {
      return Arrays.hashCode(value);
    }

    @Override
    public boolean isEqual(byte[] val1, byte[] val2) {
      return Arrays.equals(val1, val2);
    }
  }
}

