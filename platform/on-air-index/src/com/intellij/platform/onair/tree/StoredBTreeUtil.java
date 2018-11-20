// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import java.io.PrintStream;

import static com.intellij.platform.onair.tree.BTree.BYTES_PER_ADDRESS;
import static com.intellij.platform.onair.tree.ByteUtils.writeUnsignedLong;

public class StoredBTreeUtil {

  public static void set(int pos, byte[] key, int bytesPerKey, byte[] backingArray, long lowAddressBytes) {
    final int offset = (bytesPerKey + BYTES_PER_ADDRESS) * pos;

    // write key
    System.arraycopy(key, 0, backingArray, offset, bytesPerKey);
    // write address
    writeUnsignedLong(lowAddressBytes, 8, backingArray, offset + bytesPerKey);
    writeUnsignedLong(0, 8, backingArray, offset + bytesPerKey + 8);
  }

  public static void set(int pos, byte[] key, int bytesPerKey, byte[] backingArray, byte[] inlineValue) {
    int offset = (bytesPerKey + BYTES_PER_ADDRESS) * pos;

    // write key
    System.arraycopy(key, 0, backingArray, offset, bytesPerKey);
    // write value
    offset += bytesPerKey;
    System.arraycopy(inlineValue, 0, backingArray, offset, inlineValue.length);
    backingArray[offset + BYTES_PER_ADDRESS - 1] = (byte)inlineValue.length;
  }

  public static void setChild(int pos, int bytesPerKey, byte[] backingArray, long lowAddressBytes, long highAddressBytes) {
    final int offset = (bytesPerKey + BYTES_PER_ADDRESS) * pos;
    // write address
    writeUnsignedLong(lowAddressBytes, 8, backingArray, offset + bytesPerKey);
    writeUnsignedLong(highAddressBytes, 8, backingArray, offset + bytesPerKey + 8);
  }

  public static void setChild(int pos, int bytesPerKey, byte[] backingArray, byte[] inlineValue) {
    final int offset = (bytesPerKey + BYTES_PER_ADDRESS) * pos + bytesPerKey;
    // write value
    System.arraycopy(inlineValue, 0, backingArray, offset, inlineValue.length);
    backingArray[offset + BYTES_PER_ADDRESS - 1] = (byte)inlineValue.length;
  }

  public static void indent(PrintStream out, int level) {
    for (int i = 0; i < level; i++) out.print(" ");
  }
}
