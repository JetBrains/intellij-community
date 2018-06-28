// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import org.jetbrains.annotations.NotNull;

public class TestByteUtils {

  public static long readUnsignedInt(@NotNull final byte[] key) {
    final long c1 = key[0] & 0xff;
    final long c2 = key[1] & 0xff;
    final long c3 = key[2] & 0xff;
    final long c4 = key[3] & 0xff;
    if ((c1 | c2 | c3 | c4) < 0) {
      throw new IllegalArgumentException();
    }
    return ((c1 << 24) | (c2 << 16) | (c3 << 8) | c4);
  }

  public static void writeUnsignedInt(long val, byte[] result) {
    result[0] = (byte)(val >>> 24);
    result[1] = (byte)(val >>> 16);
    result[2] = (byte)(val >>> 8);
    result[3] = (byte)val;
  }
}
