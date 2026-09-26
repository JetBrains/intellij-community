// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;

@ApiStatus.Internal
public final class ConstantPoolParser {
  private final @NotNull DataInput myInput;
  private final @NotNull Callback myCallback;

  public ConstantPoolParser(@NotNull DataInput input, @NotNull Callback callback) {
    myInput = input;
    myCallback = callback;
  }

  public void parse() throws IOException, ClassFormatException {
    int magic = myInput.readInt();
    if(magic != 0xCAFEBABE) {
      throw new ClassFormatException("Invalid class file: magic incorrect");
    }

    // version
    myInput.readShort();
    myInput.readShort();

    int constantPoolCount = myInput.readShort();

    for(int i = 0; i < constantPoolCount - 1;) {
      i += readConstant();
    }
  }

  /**
   * @return  number of slots occupied in constant pool
   */
  @SuppressWarnings("DuplicateBranchesInSwitch")
  private int readConstant() throws IOException, ClassFormatException {
    int tag = myInput.readByte();
    switch (tag) {
      case 1 -> {
        String utf8String = myInput.readUTF();
        myCallback.onUtf8(utf8String); // Utf8
      }
      case 3 -> // Integer
        myInput.skipBytes(4); // Integer value
      case 4 -> // Float
        myInput.skipBytes(4); // Float value
      case 5 -> {// Long
        myInput.skipBytes(8); // Long value
        return 2;
      }
      case 6 -> { // Double
        myInput.skipBytes(8); // Double value
        return 2;
      }
      case 7 -> // Class
        myInput.skipBytes(2); // Class index
      case 8 -> // String
        myInput.skipBytes(2); // String index
      case 9 -> { // Fieldref
        myInput.skipBytes(4); // Class index + NameAndType index
      }
      case 10 -> { // Methodref
        myInput.skipBytes(4); // Class index + NameAndType index
      }
      case 11 -> { // InterfaceMethodref
        myInput.skipBytes(4); // Class index + NameAndType index
      }
      case 12 -> { // NameAndType
        myInput.skipBytes(4); // Name index + Type index
      }
      case 15 -> { // MethodHandle
        myInput.skipBytes(3); // Reference kind + Reference index
      }
      case 16 -> // MethodType
        myInput.skipBytes(2); // Descriptor index
      case 17 -> { // Dynamic
        myInput.skipBytes(4); // Bootstrap method index + NameAndType index
      }
      case 18 -> { // InvokeDynamic
        myInput.skipBytes(4); // Bootstrap method index + NameAndType index
      }
      case 19 -> // Module
        myInput.skipBytes(2); // Name index
      case 20 -> // Package
        myInput.skipBytes(2); // Name index
      default -> throw new ClassFormatException("Invalid constant pool entry tag: " + tag);
    }
    return 1;
  }

  public interface Callback {
    void onUtf8(String str);
  }

  public static class ClassFormatException extends Exception {
    ClassFormatException(String message) {
      super(message);
    }
  }

  public static void main(String[] args) {
    System.out.println("Is java id \uD83D\uDC69\u200D: " + StringUtil.isJavaIdentifier("\uD83D\uDC69\u200D"));
  }
}
