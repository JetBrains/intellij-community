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
}
