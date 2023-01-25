// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

final class InstructionKey implements Comparable<InstructionKey> {
  private final int myOffset;
  private final int[] myCallStack; // shared between instructions on the same stack level

  private InstructionKey(int offset, int @NotNull [] callStack) {
    myOffset = offset;
    myCallStack = callStack;
  }

  @NotNull
  static InstructionKey create(int offset) {
    return new InstructionKey(offset, ArrayUtilRt.EMPTY_INT_ARRAY);
  }

  @NotNull
  InstructionKey next(int nextOffset) {
    return new InstructionKey(nextOffset, myCallStack);
  }

  @NotNull
  InstructionKey push(int nextOffset, int returnOffset) {
    if(myCallStack.length > 100) { // normally it's way below 100, as it's the number of levels of nested 'finally' blocks
      throw new OverflowException(myOffset); // most likely the graph traversal is in an endless loop
    }
    int[] nextStack = ArrayUtil.append(myCallStack, returnOffset);
    return new InstructionKey(nextOffset, nextStack);
  }

  @NotNull
  InstructionKey pop(int overriddenOffset) {
    int returnOffset = myCallStack[myCallStack.length - 1];
    int[] nextStack = ArrayUtil.realloc(myCallStack, myCallStack.length - 1);
    int nextOffset = overriddenOffset != 0 ? overriddenOffset : returnOffset;
    return new InstructionKey(nextOffset, nextStack);
  }

  int getOffset() {
    return myOffset;
  }

  int @NotNull [] getCallStack() {
    return myCallStack;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    InstructionKey key = (InstructionKey)o;
    return myOffset == key.myOffset && Arrays.equals(myCallStack, key.myCallStack);
  }

  @Override
  public int hashCode() {
    return 31 * myOffset + Arrays.hashCode(myCallStack);
  }

  @Override
  public String toString() {
    if (myCallStack.length == 0) {
      return String.valueOf(myOffset);
    }
    StringBuilder s = new StringBuilder();
    for (int offset : myCallStack) {
      if (s.length() != 0) s.append(',');
      s.append(offset);
    }
    return myOffset + "[" + s + "]";
  }

  @Override
  public int compareTo(@NotNull InstructionKey key) {
    int c = myOffset - key.myOffset;
    if (c != 0) return c;
    for (int i = 0, len = Math.min(myCallStack.length, key.myCallStack.length); i < len; i++) {
      c = myCallStack[i] - key.myCallStack[i];
      if (c != 0) return c;
    }
    c = myCallStack.length - key.myCallStack.length;
    return c;
  }

  static class OverflowException extends RuntimeException {
    OverflowException(int offset) {
      super("Instruction key overflow at offset " + offset);
    }
  }
}
