/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.util.io.DataInputOutputUtil;
import org.iq80.snappy.CorruptionException;
import org.iq80.snappy.Snappy;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 * @author Maxim.Mossienko
 */
public class CompressionUtil {
  private static final int COMPRESSION_THRESHOLD = 64;
  private static final ThreadLocal<SoftReference<byte[]>> spareBufferLocal = new ThreadLocal<SoftReference<byte[]>>();

  public static int writeCompressed(DataOutput out, byte[] bytes, int length) throws IOException {
    if (length > COMPRESSION_THRESHOLD) {
      SoftReference<byte[]> reference = spareBufferLocal.get();
      byte[] compressedOutputBuffer = com.intellij.reference.SoftReference.dereference(reference);
      int maxCompressedSize = Snappy.maxCompressedLength(length);

      if (compressedOutputBuffer == null || compressedOutputBuffer.length < maxCompressedSize) {
        compressedOutputBuffer = new byte[maxCompressedSize];
        spareBufferLocal.set(new SoftReference<byte[]>(compressedOutputBuffer));
      }
      int compressedSize = Snappy.compress(bytes, 0, length, compressedOutputBuffer, 0);
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
      return Snappy.uncompress(bytes, 0, bytes.length);
    }
  }

  private static final int STRING_COMPRESSION_THRESHOLD = 1024;

  public static CharSequence uncompressCharSequence(Object string, Charset charset) {
    if (string instanceof CharSequence) return (CharSequence)string;
    byte[] b = (byte[])string;
    try {
      byte[] bytes = Snappy.uncompress(b, 0, b.length);
      return new String(bytes, charset);
    } catch (CorruptionException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static Object compressCharSequence(CharSequence string, Charset charset) {
    if (string.length() < STRING_COMPRESSION_THRESHOLD) {
      if (string instanceof CharBuffer && ((CharBuffer)string).capacity() > STRING_COMPRESSION_THRESHOLD) {
        string = string.toString();   // shrink to size
      }
      return string;
    }
    try {
      return Snappy.compress(string.toString().getBytes(charset));
    } catch (CorruptionException ex) {
      ex.printStackTrace();
      return string;
    }
  }
}
