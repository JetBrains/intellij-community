/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.controlflow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;

import java.util.Arrays;

/**
 * @author oleg
 */
public class ControlFlowUtil {
    private static final Logger LOG = Logger.getInstance(ControlFlowUtil.class.getName());

  private ControlFlowUtil() {
  }

  public static class Stack {
    private final int myCapacity;
    private final int[] myValues;
    private int myIndex;

    public Stack(final int capacity) {
      myCapacity = capacity;
      myValues = new int[myCapacity];
      clear();
    }

    public void push(final int value) {
      myValues[++myIndex] = value;
    }

    public int pop() {
      assert !isEmpty() : "Cannot pop on empty stack";
      return myValues[myIndex--];
    }

    public boolean isEmpty() {
      return myIndex == -1;
    }

    public void clear() {
      myIndex = -1;
    }
  }

  public static int[] postOrder(Instruction[] flow) {
    final int length = flow.length;
    int[] result = new int[length];
    boolean[] visited = new boolean[length];
    Arrays.fill(visited, false);
    final Stack stack = new Stack(length);

    int N = 0;
    for (int i = 0; i < length; i++) { //graph might not be connected
      if (!visited[i]) {
        visited[i] = true;
        stack.clear();
        stack.push(i);

        while (!stack.isEmpty()) {
          final int num = stack.pop();
          result[num] = N++;
          for (Instruction succ : flow[num].allSucc()) {
            final int succNum = succ.num();
            if (!visited[succNum]) {
              visited[succNum] = true;
              stack.push(succNum);
            }
          }
        }
      }
    }
    LOG.assertTrue(N == length);
    return result;
  }

  public static int findInstructionNumberByElement(final Instruction[] flow, final PsiElement element){
    for (int i=0;i<flow.length;i++) {
      if (element == flow[i].getElement()){
        return i;
      }
    }
    return -1;
  }
}