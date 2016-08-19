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
package com.intellij.psi.controlFlow;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author Pavel.Dolgov
 */
class InstructionKey implements Comparable<InstructionKey> {
  private final int myOffset;
  private final int[] myCallStack; // shared between instructions on the same stack level

  private InstructionKey(int offset, @NotNull int[] callStack) {
    myOffset = offset;
    myCallStack = callStack;
  }

  @NotNull
  static InstructionKey create(int offset) {
    return new InstructionKey(offset, ArrayUtil.EMPTY_INT_ARRAY);
  }

  InstructionKey next(int nextOffset) {
    return new InstructionKey(nextOffset, myCallStack);
  }

  InstructionKey push(int nextOffset, int returnOffset) {
    int[] nextStack = ArrayUtil.append(myCallStack, returnOffset);
    return new InstructionKey(nextOffset, nextStack);
  }

  InstructionKey pop(int overriddenOffset) {
    int returnOffset = myCallStack[myCallStack.length - 1];
    int[] nextStack = ArrayUtil.realloc(myCallStack, myCallStack.length - 1);
    int nextOffset = overriddenOffset != 0 ? overriddenOffset : returnOffset;
    return new InstructionKey(nextOffset, nextStack);
  }

  int getOffset() {
    return myOffset;
  }

  int[] getCallStack() {
    return myCallStack;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    InstructionKey key = (InstructionKey)o;

    if (myOffset != key.myOffset) return false;
    if (!Arrays.equals(myCallStack, key.myCallStack)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myOffset;
    result = 31 * result + Arrays.hashCode(myCallStack);
    return result;
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
    return myOffset + "(" + s + ")";
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
}
