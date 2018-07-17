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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.IntStack;
import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author oleg
 */
public class ControlFlowUtil {
  private static final Logger LOG = Logger.getInstance(ControlFlowUtil.class.getName());

  private ControlFlowUtil() {
  }

  @NotNull
  public static Graph<Instruction> createGraph(@NotNull final Instruction[] flow) {
    return new Graph<Instruction>() {
      @NotNull
      final private List<Instruction> myList = Arrays.asList(flow);

      @NotNull
      @Override
      public Collection<Instruction> getNodes() {
        return myList;
      }

      @NotNull
      @Override
      public Iterator<Instruction> getIn(Instruction n) {
        return n.allPred().iterator();
      }

      @NotNull
      @Override
      public Iterator<Instruction> getOut(Instruction n) {
        return n.allSucc().iterator();
      }
    };
  }

  public static int findInstructionNumberByElement(final Instruction[] flow, final PsiElement element){
    for (int i=0;i<flow.length;i++) {
      // Check if canceled
      ProgressManager.checkCanceled();

      if (element == flow[i].getElement()){
        return i;
      }
    }
    return -1;
  }

  /**
   * Process control flow graph in depth first order
   */
  public static boolean process(final Instruction[] flow, final int start, final Processor<Instruction> processor){
    final int length = flow.length;
    boolean[] visited = new boolean[length];
    Arrays.fill(visited, false);

    final IntStack stack = new IntStack(length);
    stack.push(start);

    while (!stack.empty()) {
      ProgressManager.checkCanceled();
      final int num = stack.pop();
      final Instruction instruction = flow[num];
      if (!processor.process(instruction)){
        return false;
      }
      for (Instruction succ : instruction.allSucc()) {
        final int succNum = succ.num();
        if (!visited[succNum]) {
          visited[succNum] = true;
          stack.push(succNum);
        }
      }
    }
    return true;
  }

  /**
   * Iterates over write instructions in CFG with reversed order
   */
  public static void iteratePrev(final int startInstruction,
                                 @NotNull final Instruction[] instructions,
                                 @NotNull final Function<Instruction, Operation> closure) {
    final IntStack stack = new IntStack(instructions.length);
    final boolean[] visited = new boolean[instructions.length];

    stack.push(startInstruction);
    while (!stack.empty()) {
      ProgressManager.checkCanceled();
      final int num = stack.pop();
      final Instruction instr = instructions[num];
      final Operation nextOperation = closure.fun(instr);
      // Just ignore previous instructions for current node and move further
      if (nextOperation == Operation.CONTINUE) {
        continue;
      }
      // STOP iteration
      if (nextOperation == Operation.BREAK) {
        break;
      }
      // If we are here, we should process previous nodes in natural way
      assert nextOperation == Operation.NEXT;
      for (Instruction pred : instr.allPred()) {
        final int predNum = pred.num();
        if (!visited[predNum]) {
          visited[predNum] = true;
          stack.push(predNum);
        }
      }
    }
  }

  public enum Operation {
    /**
     * CONTINUE is used to ignore previous elements processing for the node, however it doesn't stop the iteration process
     */
    CONTINUE,
    /**
     * BREAK is used to stop iteration process
     */
    BREAK,
    /**
     * NEXT is used to indicate that iteration should be continued in natural way
     */
    NEXT
  }
}