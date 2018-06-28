// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import org.jetbrains.annotations.NotNull;

public class ByteUtils {

  public static int compare(@NotNull final byte[] key1, final int len1, final int offset1,
                            @NotNull final byte[] key2, final int len2, final int offset2) {
    final int min = Math.min(len1, len2);

    for (int i = 0; i < min; i++) {
      final byte b1 = key1[i + offset1];
      final byte b2 = key2[i + offset2];
      if (b1 != b2) {
        return (b1 & 0xff) - (b2 & 0xff);
      }
    }

    return len1 - len2;
  }

  public static long readUnsignedLong(@NotNull final byte[] bytes, final int offset, final int length) {
    long result = 0;
    for (int i = 0; i < length; ++i) {
      result = (result << 8) + ((int)bytes[offset + i] & 0xff);
    }
    return result;
  }

  public static void writeUnsignedLong(final long l,
                                       final int bytesPerLong,
                                       @NotNull final byte[] output,
                                       int offset) {
    int bits = bytesPerLong << 3;
    while (bits > 0) {
      output[offset++] = ((byte)(l >> (bits -= 8) & 0xff));
    }
  }


  public static long readUnsignedInt(@NotNull final byte[] key, int offset) {
    final long c1 = key[offset + 0] & 0xff;
    final long c2 = key[offset + 1] & 0xff;
    final long c3 = key[offset + 2] & 0xff;
    final long c4 = key[offset + 3] & 0xff;
    if ((c1 | c2 | c3 | c4) < 0) {
      throw new IllegalArgumentException();
    }
    return ((c1 << 24) | (c2 << 16) | (c3 << 8) | c4);
  }

  public static void writeUnsignedInt(long val, byte[] result, int offset) {
    result[offset + 0] = (byte)(val >>> 24);
    result[offset + 1] = (byte)(val >>> 16);
    result[offset + 2] = (byte)(val >>> 8);
    result[offset + 3] = (byte)val;
  }
  public static long normalizeLowBytes(long address) {
    if (address < 0) {
      return address;
    }
    if (address == 0) {
      return Long.MIN_VALUE;
    }
    return -address;
  }

  public static byte[] toBytes(int x) {
    byte[] bytes = new byte[4];
    writeUnsignedInt(x, bytes, 0);
    return bytes;
  }
}
