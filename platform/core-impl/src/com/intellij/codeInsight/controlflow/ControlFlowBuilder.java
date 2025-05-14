// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.impl.ConditionalInstructionImpl;
import com.intellij.codeInsight.controlflow.impl.ControlFlowImpl;
import com.intellij.codeInsight.controlflow.impl.InstructionImpl;
import com.intellij.codeInsight.controlflow.impl.TransparentInstructionImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ControlFlowBuilder {

  private static final Logger LOG = Logger.getInstance(ControlFlowBuilder.class);

  // Here we store all the instructions
  public List<Instruction> instructions;

  // The last instruction
  public Instruction prevInstruction;

  // Here we store all the pending instructions with their scope
  public List<Pair<PsiElement, Instruction>> pending;

  // Number of instructions already added
  public int instructionCount;
  public int transparentInstructionCount;

  public ControlFlowBuilder() {
    instructions = new ArrayList<>();
    pending = new ArrayList<>();
    instructionCount = 0;
    transparentInstructionCount = 0;
  }

  public @Nullable Instruction findInstructionByElement(final PsiElement element) {
    for (int i = instructions.size() - 1; i >= 0; i--) {
      final Instruction instruction = instructions.get(i);
      if (element.equals(instruction.getElement())) {
        return instruction;
      }
    }
    return null;
  }

  /**
   * @return "raw" current state of control flow
   */
  public final @NotNull ControlFlow getControlFlow() {
    return new ControlFlowImpl(instructions.toArray(Instruction.EMPTY_ARRAY));
  }

  /**
   * Mutates instructions in place.
   *
   * @return control flow without transparent instructions
   */
  public final @NotNull ControlFlow completeControlFlow() {
    if (transparentInstructionCount == 0) return getControlFlow();

    ArrayList<Instruction> result = new ArrayList<>(instructionCount);
    int processedTransparentInstructions = 0;
    for (Instruction instruction : instructions) {
      if (instruction instanceof TransparentInstruction) {
        processedTransparentInstructions++;

        Collection<Instruction> predecessors = instruction.allPred();
        Collection<Instruction> successors = instruction.allSucc();

        for (Instruction predecessor : predecessors) {
          predecessor.replaceSucc(instruction, successors);
        }

        for (Instruction successor : successors) {
          successor.replacePred(instruction, predecessors);
        }
      }
      else {
        result.add(instruction);
      }
    }

    if (result.size() != instructionCount || processedTransparentInstructions != transparentInstructionCount) {
      LOG.error("Control flow graph is inconsistent. Instructions: (" + result.size() + ", " + instructionCount + ")\n" +
                "Transparent instructions: (" + processedTransparentInstructions + ", " + transparentInstructionCount + ")");
    }

    if (!pending.isEmpty()) {
      LOG.error("Control flow is used incorrectly. All pending node must be connected to fake exit point. Pending: " + pending);
    }

    return new ControlFlowImpl(result.toArray(Instruction.EMPTY_ARRAY));
  }


  /**
   * Adds edge between 2 edges
   *
   * @param beginInstruction Begin of new edge
   * @param endInstruction   End of new edge
   */
  public void addEdge(final @Nullable Instruction beginInstruction, final @Nullable Instruction endInstruction) {
    if (beginInstruction == null || endInstruction == null) {
      return;
    }
    beginInstruction.addSucc(endInstruction);
    endInstruction.addPred(beginInstruction);
  }

  /**
   * Add new node to the instructions list and set prev instruction pointing to this instruction
   *
   * @param instruction new instruction
   */
  public final void addNode(final @NotNull Instruction instruction) {
    instructions.add(instruction);
    if (prevInstruction != null) {
      addEdge(prevInstruction, instruction);
    }
    prevInstruction = instruction;
  }

  /**
   * Add a new node to the instructions list and update prev and pending instruction
   *
   * @param instruction new instruction
   */
  public final void addNodeAndCheckPending(final Instruction instruction) {
    addNode(instruction);
    checkPending(instruction);
  }

  /**
   * Stops control flow, used for break, next, redo, continue
   *
   * @apiNote please make sure that the prev instruction will be connected to exit point if it doesn't have any successors
   */
  @SuppressWarnings("SpellCheckingInspection")
  public final void flowAbrupted() {
    prevInstruction = null;
  }

  /**
   * Adds pending edge in pendingScope
   * Pending instruction are used when you have several branches from previous statement
   * Also you can add a pending instruction for the 'exit point' of the all scope
   *
   * @param pendingScope Scope for instruction / null if expected scope = exit point
   * @param instruction  "Last" pending instruction
   */
  public void addPendingEdge(final @Nullable PsiElement pendingScope, final @Nullable Instruction instruction) {
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
  public final void checkPending(final @NotNull Instruction instruction) {
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
   * Creates instruction for given element, and adds it to the instructions stack
   *
   * @param element Element to create instruction for
   * @return new instruction
   */
  public @NotNull Instruction startNode(final @Nullable PsiElement element) {
    final Instruction instruction = new InstructionImpl(this, element);
    addNodeAndCheckPending(instruction);
    return instruction;
  }

  /**
   * Creates transparent instruction for given element, and adds it to the instructions list
   * Transparent instruction will be replaced in the result control flow by direct connection between predecessors and successors
   *
   * @param element Element to create instruction for
   * @param markerName name for debug information
   * @return new transparent instruction
   */
  public final @NotNull TransparentInstruction startTransparentNode(final @Nullable PsiElement element, String markerName) {
    final TransparentInstruction instruction = new TransparentInstructionImpl(this, element, markerName);
    addNodeAndCheckPending(instruction);
    return instruction;
  }

  /**
   * Creates conditional instruction for given element, and adds it to the instructions stack
   *
   * @param element Element to create instruction for
   * @return new instruction
   */
  @SuppressWarnings("UnusedReturnValue")
  public final Instruction startConditionalNode(final PsiElement element, final PsiElement condition, final boolean result) {
    final ConditionalInstruction instruction = new ConditionalInstructionImpl(this, element, condition, result);
    addNodeAndCheckPending(instruction);
    return instruction;
  }

  public final @NotNull ControlFlow build(@NotNull PsiElementVisitor visitor, @NotNull PsiElement element) {
    visitFor(visitor, element);
    return completeControlFlow();
  }

  public final void visitFor(@NotNull PsiElementVisitor visitor, @NotNull PsiElement element) {
    addEntryPointNode(element);

    element.acceptChildren(visitor);

    // create end pseudo node and close all pending edges
    Instruction exitInstruction = startNode(null);
    checkPending(exitInstruction);
  }

  protected void addEntryPointNode(final PsiElement startElement) {
    // create start pseudo node
    startNode(null);
  }

  public final void updatePendingElementScope(@NotNull PsiElement parentForScope,
                                        @Nullable PsiElement newParentScope) {
    processPending((pendingScope, instruction) -> {
      if (pendingScope != null && PsiTreeUtil.isAncestor(parentForScope, pendingScope, false)) {
        addPendingEdge(newParentScope, instruction);
      }
      else {
        addPendingEdge(pendingScope, instruction);
      }
    });
  }


  @FunctionalInterface
  public interface PendingProcessor {
    void process(@Nullable PsiElement pendingScope, @NotNull Instruction instruction);
  }

  public void processPending(@NotNull PendingProcessor processor) {
    final List<Pair<PsiElement, Instruction>> pending = this.pending;
    this.pending = new ArrayList<>();
    for (Pair<PsiElement, Instruction> pair : pending) {
      processor.process(pair.getFirst(), pair.getSecond());
    }
  }
}
