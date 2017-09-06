/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.controlflow.impl.ConditionalInstructionImpl;
import com.intellij.codeInsight.controlflow.impl.ControlFlowImpl;
import com.intellij.codeInsight.controlflow.impl.InstructionImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
* @author oleg
*/
public class ControlFlowBuilder {
  // Here we store all the instructions
  public List<Instruction> instructions;

  // The last instruction
  public Instruction prevInstruction;

  // Here we store all the pending instructions with their scope
  public List<Pair<PsiElement, Instruction>> pending;

  // Number of instructions already added
  public int instructionCount;

  public ControlFlowBuilder() {
    instructions = ContainerUtil.newArrayList();
    pending = ContainerUtil.newArrayList();
    instructionCount = 0;
  }

  @Nullable
  public Instruction findInstructionByElement(final PsiElement element) {
    for (int i = instructions.size() - 1; i >= 0; i--) {
      final Instruction instruction = instructions.get(i);
      if (element.equals(instruction.getElement())) {
        return instruction;
      }
    }
    return null;
  }

  /**
   * Adds edge between 2 edges
   *
   * @param beginInstruction Begin of new edge
   * @param endInstruction   End of new edge
   */
  public void addEdge(final Instruction beginInstruction, final Instruction endInstruction) {
    if (beginInstruction == null || endInstruction == null) {
      return;
    }
    if (!beginInstruction.allSucc().contains(endInstruction)) {
      beginInstruction.allSucc().add(endInstruction);
    }

    if (!endInstruction.allPred().contains(beginInstruction)) {
      endInstruction.allPred().add(beginInstruction);
    }
  }

  /**
   * Add new node and set prev instruction pointing to this instruction
   *
   * @param instruction new instruction
   */
  public void addNode(final Instruction instruction) {
    instructions.add(instruction);
    if (prevInstruction != null) {
      addEdge(prevInstruction, instruction);
    }
    prevInstruction = instruction;
  }

  /**
   * Stops control flow, used for break, next, redo
   */
  public void flowAbrupted() {
    prevInstruction = null;
  }

  /**
   * Adds pending edge in pendingScope
   *
   * @param pendingScope Scope for instruction
   * @param instruction  "Last" pending instruction
   */
  public void addPendingEdge(@Nullable final PsiElement pendingScope, @Nullable final Instruction instruction) {
    if (instruction == null) {
      return;
    }

    int i = 0;
    // another optimization! Place pending before first scope, not contained in pendingScope
    // the same logic is used in checkPending
    if (pendingScope != null) {
      for (; i < pending.size(); i++) {
        final Pair<PsiElement, Instruction> pair = pending.get(i);
        final PsiElement scope = pair.getFirst();
        if (scope == null) {
          continue;
        }
        if (!PsiTreeUtil.isAncestor(scope, pendingScope, true)) {
          break;
        }
      }
    }
    pending.add(i, Pair.create(pendingScope, instruction));
  }

  /**
   * Creates edges from the pending list to the specified instruction.
   *
   * @param instruction target instruction for pending edges
   */
  public void checkPending(@NotNull final Instruction instruction) {
    final PsiElement element = instruction.getElement();
    if (element == null) {
      // if element is null (fake element, we just process all pending)
      for (Pair<PsiElement, Instruction> pair : pending) {
        addEdge(pair.getSecond(), instruction);
      }
      pending.clear();
    }
    else {
      // else we just process all the pending with scope containing in element
      // reverse order is just an optimization
      for (int i = pending.size() - 1; i >= 0; i--) {
        final Pair<PsiElement, Instruction> pair = pending.get(i);
        final PsiElement scopeWhenToAdd = pair.getFirst();
        if (scopeWhenToAdd == null) {
          continue;
        }
        if (!PsiTreeUtil.isAncestor(scopeWhenToAdd, element, false)) {
          addEdge(pair.getSecond(), instruction);
          pending.remove(i);
        }
        else {
          break;
        }
      }
    }
  }

  /**
   * Creates instruction for given element, and adds it to myInstructionsStack
   *
   * @param element Element to create instruction for
   * @return new instruction
   */
  public Instruction startNode(@Nullable final PsiElement element) {
    final Instruction instruction = new InstructionImpl(this, element);
    addNode(instruction);
    checkPending(instruction);
    return instruction;
  }

  /**
   * Creates conditional instruction for given element, and adds it to myInstructionsStack
   *
   * @param element Element to create instruction for
   * @return new instruction
   */
  public Instruction startConditionalNode(final PsiElement element, final PsiElement condition, final boolean result) {
    final ConditionalInstruction instruction = new ConditionalInstructionImpl(this, element, condition, result);
    addNode(instruction);
    checkPending(instruction);
    return instruction;
  }

  public ControlFlow build(final PsiElementVisitor visitor, final PsiElement element) {
    // create start pseudo node
    startNode(null);

    element.acceptChildren(visitor);

    // create end pseudo node and close all pending edges
    checkPending(startNode(null));

    final List<Instruction> result = instructions;
    return new ControlFlowImpl(result.toArray(new Instruction[result.size()]));
  }

  @Nullable
  public Instruction getPrevInstruction(@Nullable final PsiElement condition) {
    final Ref<Instruction> head = new Ref<>(prevInstruction);
    processPending((pendingScope, instruction) -> {
      if (pendingScope != null && PsiTreeUtil.isAncestor(condition, pendingScope, false)) {
        head.set(instruction);
      }
      else {
        addPendingEdge(pendingScope, instruction);
      }
    });
    return head.get();
  }

  
  
  @FunctionalInterface
  public interface PendingProcessor {
    void process(PsiElement pendingScope, Instruction instruction);
  }

  public void processPending(final PendingProcessor processor) {
    final List<Pair<PsiElement, Instruction>> pending = this.pending;
    this.pending = new ArrayList<>();
    for (Pair<PsiElement, Instruction> pair : pending) {
      processor.process(pair.getFirst(), pair.getSecond());
    }
  }
}
