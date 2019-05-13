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

import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Stores instruction keys (such a key is offset + call stack), compares sub-stacks instead of whole stacks in the following way:
 * if a set contains a key having offset N and the stack [A,B,C], the set behaves as if it contains any keys like N[A, B, C, *, ...] as well.
 * This logic relies on the control flow graph traversal order where the direct path (offset->offset+1) is handled immediately,
 * and the alternative path (e.g. in COND_THROW and COND_GOTO) is handled later.
 * <p>
 * Logically equivalent instruction keys having different stacks (but equal sub-stacks) may appear
 * when a 'finally' block is exited with THROW or COND_THROW instead of RETURN, so that the stack isn't unwound.
 * In this case the top of the stack is no longer relevant and should be ignored, which is done by comparing sub-stacks.
 *
 * @author Pavel.Dolgov
 */
class InstructionKeySet {
  private final @NotNull Node myRoot;

  InstructionKeySet(int initialCapacity) {
    this.myRoot = new Node(initialCapacity);
  }

  void add(@NotNull InstructionKey key) {
    myRoot.add(key.getOffset(), key.getCallStack(), 0);
  }

  boolean contains(@NotNull InstructionKey key) {
    return myRoot.contains(key.getOffset(), key.getCallStack(), 0);
  }

  @Override
  public String toString() {
    return myRoot.toString();
  }

  /**
   * If the instruction key N is in the set it's represented as N->null.
   * If the instruction key N[A,B,C] is in the set it's represented as N->A->B->C->null.
   */
  private static class Node extends TIntObjectHashMap<Node> {
    private Node(int initialCapacity) {
      super(Math.max(initialCapacity, 2));
    }

    private void add(int offset, @NotNull int[] stack, int level) {
      if (level < stack.length) {
        Node node = get(offset);
        if (node == null) {
          node = new Node(4);
          put(offset, node);
        }
        node.add(stack[level], stack, level + 1);
      }
      else {
        if (!containsKey(offset)) {
          put(offset, null);
        }
      }
    }

    private boolean contains(int offset, @NotNull int[] stack, int level) {
      if (level < stack.length) {
        Node node = get(offset);
        if (node != null) {
          return node.contains(stack[level], stack, level + 1);
        }
      }
      return containsKey(offset);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("{");
      int[] offsets = keys();
      Arrays.sort(offsets);
      for (int offset : offsets) {
        if (sb.length() > 1) sb.append(", ");
        sb.append(offset);
        Node node = get(offset);
        if (node != null) {
          sb.append(node);
        }
      }
      return sb.append("}").toString();
    }
  }
}
