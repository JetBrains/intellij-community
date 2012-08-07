/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.indexing;

import com.intellij.idea.StartupUtil;
import com.intellij.util.io.DataInputOutputUtil;
import org.xerial.snappy.Snappy;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;

/**
 * @author Maxim.Mossienko
 */
public class IOUtils {
  private static final boolean ourCanUseSnappy;

  static {
    boolean canUseSnappy = false;
    try {
      if (!StartupUtil.NO_SNAPPY) {
        Field impl = Snappy.class.getDeclaredField("impl");
        impl.setAccessible(true);
        canUseSnappy = impl.get(null) != null;
      }
    }
    catch (Throwable ignored) { }

    ourCanUseSnappy = canUseSnappy;
  }

  private static final int COMPRESSION_THRESHOLD = 64;
  private static final ThreadLocal<SoftReference<byte[]>> spareBufferLocal = new ThreadLocal<SoftReference<byte[]>>();

  public static int writeCompressed(DataOutput out, byte[] bytes, int length) throws IOException {
    if (length > COMPRESSION_THRESHOLD && ourCanUseSnappy) {
      SoftReference<byte[]> reference = spareBufferLocal.get();
      byte[] compressedOutputBuffer = reference != null ? reference.get():null;
      int maxCompressedSize = 32 + length + length / 6; // snappy.cc#MaxCompressedLength
      if (compressedOutputBuffer == null || compressedOutputBuffer.length < maxCompressedSize) {
        compressedOutputBuffer = new byte[maxCompressedSize];
        spareBufferLocal.set(new SoftReference<byte[]>(compressedOutputBuffer));
      }
      int compressedSize = Snappy.rawCompress(bytes, 0, length, compressedOutputBuffer, 0);
      DataInputOutputUtil.writeINT(out, -compressedSize);
      out.write(compressedOutputBuffer, 0, compressedSize);
      return compressedSize;
    } else {
      DataInputOutputUtil.writeINT(out, length);
      out.write(bytes, 0, length);
      return length;
    }
  }

  public static byte[] readCompressed(DataInput in) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    byte[] bytes = new byte[Math.abs(size)];
    in.readFully(bytes);
    if (size >= 0) {
      return bytes;
    } else {
      if (!ourCanUseSnappy) throw new IOException("Can not read compressed data");
      return Snappy.uncompress(bytes);
    }
  }
}
