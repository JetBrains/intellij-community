// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

import static intellij.platform.onair.tree.TestByteUtils.readUnsignedInt;

public class ValueDumper implements BTree.ToString {
  public static final ValueDumper INSTANCE = new ValueDumper();

  private ValueDumper() {
  }

  @Override
  public String renderKey(byte[] key) {
    return Integer.toString(readInt(key));
  }

  @Override
  public String renderValue(byte[] value) {
    String valueString = new String(value, Charset.forName("UTF-8"));
    if (!valueString.startsWith("val")) {
      valueString = "[ERROR]";
    }
    return valueString;
  }

  public static int readInt(@NotNull final byte[] key) {
    return (int)(readUnsignedInt(key) ^ 0x80000000);
  }
}
