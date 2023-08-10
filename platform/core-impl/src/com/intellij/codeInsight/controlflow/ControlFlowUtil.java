// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.controlflow;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.graph.Graph;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public final class ControlFlowUtil {
  private ControlFlowUtil() {
  }

  public static @NotNull Graph<Instruction> createGraph(final Instruction @NotNull [] flow) {
    return new Graph<Instruction>() {
      private final @NotNull List<Instruction> myList = Arrays.asList(flow);

      @Override
      public @NotNull Collection<Instruction> getNodes() {
        return myList;
      }

      @Override
      public @NotNull Iterator<Instruction> getIn(Instruction n) {
        return n.allPred().iterator();
      }

      @Override
      public @NotNull Iterator<Instruction> getOut(Instruction n) {
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
  public static boolean process(final Instruction[] flow, final int start, final Processor<? super Instruction> processor){
    final int length = flow.length;
    boolean[] visited = new boolean[length];
    Arrays.fill(visited, false);

    final IntStack stack = new IntArrayList(length);
    stack.push(start);

    while (!stack.isEmpty()) {
      ProgressManager.checkCanceled();
      final int num = stack.popInt();
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

  public static void iteratePrev(final int startInstruction,
                                 final Instruction @NotNull [] instructions,
                                 final @NotNull Function<? super Instruction, Operation> closure) {
    iterate(startInstruction, instructions, closure, true);
  }

  /**
   * Iterates over write instructions in CFG with reversed order
   */
  public static void iterate(final int startInstruction,
                             final Instruction @NotNull [] instructions,
                             final @NotNull Function<? super Instruction, Operation> closure,
                             boolean prev) {
    final IntStack stack = new IntArrayList(instructions.length);
    final boolean[] visited = new boolean[instructions.length];

    stack.push(startInstruction);
    while (!stack.isEmpty()) {
      ProgressManager.checkCanceled();
      final int num = stack.popInt();
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
      Collection<Instruction> nextToProcess = prev ? instr.allPred() : instr.allSucc();
      for (Instruction pred : nextToProcess) {
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
     * CONTINUE is used to ignore previous/next elements processing for the node, however it doesn't stop the iteration process
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