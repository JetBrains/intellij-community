// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.*;

/**
 * Specialized version of {@link org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer}.
 * Calculation of fix-point of frames is removed, since frames are not needed to build control flow graph.
 * So, the main point here is handling of subroutines (jsr) and try-catch-finally blocks.
 */
public class FramelessAnalyzer extends SubroutineFinder {
  private static final Set<String> NPE_HANDLERS = Set.of(
    "java/lang/Throwable", "java/lang/Exception", "java/lang/RuntimeException", "java/lang/NullPointerException");

  protected boolean[] wasQueued;
  protected boolean[] queued;
  protected int[] queue;
  protected int top;
  protected final EdgeCreator myEdgeCreator;

  public FramelessAnalyzer(EdgeCreator creator) {myEdgeCreator = creator;}

  public void analyze(MethodNode m) throws AnalyzerException {
    n = m.instructions.size();
    if ((m.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0 || n == 0) {
      return;
    }
    insns = m.instructions;
    handlers = ASMUtils.newListArray(n);
    subroutines = new Subroutine[n];
    queued = new boolean[n];
    wasQueued = new boolean[n];
    queue = new int[n];
    top = 0;

    // computes exception handlers for each instruction
    for (TryCatchBlockNode tcb : m.tryCatchBlocks) {
      int begin = insns.indexOf(tcb.start);
      int end = insns.indexOf(tcb.end);
      for (int j = begin; j < end; ++j) {
        List<TryCatchBlockNode> insnHandlers = handlers[j];
        if (insnHandlers == null) {
          insnHandlers = new ArrayList<>();
          handlers[j] = insnHandlers;
        }
        insnHandlers.add(tcb);
      }
    }

    // computes the subroutine for each instruction:
    Subroutine main = new Subroutine(null, m.maxLocals, null);
    List<AbstractInsnNode> subroutineCalls = new ArrayList<>();
    Map<LabelNode, Subroutine> subroutineHeads = new HashMap<>();

    findSubroutine(0, main, subroutineCalls);
    while (!subroutineCalls.isEmpty()) {
      JumpInsnNode jsr = (JumpInsnNode)subroutineCalls.remove(0);
      Subroutine sub = subroutineHeads.get(jsr.label);
      if (sub == null) {
        sub = new Subroutine(jsr.label, m.maxLocals, jsr);
        subroutineHeads.put(jsr.label, sub);
        findSubroutine(insns.indexOf(jsr.label), sub, subroutineCalls);
      }
      else {
        sub.callers.add(jsr);
      }
    }
    for (int i = 0; i < n; ++i) {
      if (subroutines[i] != null && subroutines[i].start == null) {
        subroutines[i] = null;
      }
    }

    merge(0, null);
    // control flow analysis
    while (top > 0) {
      int insn = queue[--top];
      Subroutine subroutine = subroutines[insn];
      queued[insn] = false;

      AbstractInsnNode insnNode = null;
      try {
        insnNode = m.instructions.get(insn);
        int insnOpcode = insnNode.getOpcode();
        int insnType = insnNode.getType();

        if (insnType == AbstractInsnNode.LABEL || insnType == AbstractInsnNode.LINE || insnType == AbstractInsnNode.FRAME) {
          merge(insn + 1, subroutine);
          myEdgeCreator.newControlFlowEdge(insn, insn + 1);
        }
        else {
          subroutine = subroutine == null ? null : subroutine.copy();

          if (insnNode instanceof JumpInsnNode j) {
            if (insnOpcode != GOTO && insnOpcode != JSR) {
              merge(insn + 1, subroutine);
              myEdgeCreator.newControlFlowEdge(insn, insn + 1);
            }
            int jump = insns.indexOf(j.label);
            if (insnOpcode == JSR) {
              merge(jump, new Subroutine(j.label, m.maxLocals, j));
            }
            else {
              merge(jump, subroutine);
            }
            myEdgeCreator.newControlFlowEdge(insn, jump);
          }
          else if (insnNode instanceof LookupSwitchInsnNode lsi) {
            int jump = insns.indexOf(lsi.dflt);
            merge(jump, subroutine);
            myEdgeCreator.newControlFlowEdge(insn, jump);
            for (int j = 0; j < lsi.labels.size(); ++j) {
              LabelNode label = lsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, subroutine);
              myEdgeCreator.newControlFlowEdge(insn, jump);
            }
          }
          else if (insnNode instanceof TableSwitchInsnNode tsi) {
            int jump = insns.indexOf(tsi.dflt);
            merge(jump, subroutine);
            myEdgeCreator.newControlFlowEdge(insn, jump);
            for (int j = 0; j < tsi.labels.size(); ++j) {
              LabelNode label = tsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, subroutine);
              myEdgeCreator.newControlFlowEdge(insn, jump);
            }
          }
          else if (insnOpcode == RET) {
            if (subroutine == null) {
              throw new AnalyzerException(insnNode, "RET instruction outside of a sub routine");
            }
            for (int i = 0; i < subroutine.callers.size(); ++i) {
              JumpInsnNode caller = subroutine.callers.get(i);
              int call = insns.indexOf(caller);
              if (wasQueued[call]) {
                merge(call + 1, subroutines[call], subroutine.access);
                myEdgeCreator.newControlFlowEdge(insn, call + 1);
              }
            }
          }
          else if (insnOpcode != ATHROW && (insnOpcode < IRETURN || insnOpcode > RETURN)) {
            if (subroutine != null) {
              if (insnNode instanceof VarInsnNode) {
                int var = ((VarInsnNode)insnNode).var;
                subroutine.access[var] = true;
                if (insnOpcode == LLOAD || insnOpcode == DLOAD
                    || insnOpcode == LSTORE
                    || insnOpcode == DSTORE) {
                  subroutine.access[var + 1] = true;
                }
              }
              else if (insnNode instanceof IincInsnNode) {
                int var = ((IincInsnNode)insnNode).var;
                subroutine.access[var] = true;
              }
            }
            merge(insn + 1, subroutine);
            myEdgeCreator.newControlFlowEdge(insn, insn + 1);
          }
        }

        List<TryCatchBlockNode> insnHandlers = handlers[insn];
        if (insnHandlers != null) {
          for (TryCatchBlockNode tcb : insnHandlers) {
            myEdgeCreator.newControlFlowExceptionEdge(insn, insns.indexOf(tcb.handler), tcb.type != null && NPE_HANDLERS.contains(tcb.type));
            merge(insns.indexOf(tcb.handler), subroutine);
          }
        }
      }
      catch (AnalyzerException e) {
        throw new AnalyzerException(e.node, "Error at instruction " + insn + ": " + e.getMessage(), e);
      }
      catch (Exception e) {
        throw new AnalyzerException(insnNode, "Error at instruction " + insn + ": " + e.getMessage(), e);
      }
    }
  }

  // -------------------------------------------------------------------------

  protected void merge(int insn, @Nullable Subroutine subroutine) {
    Subroutine oldSubroutine = subroutines[insn];
    boolean changes = false;

    if (!wasQueued[insn]) {
      wasQueued[insn] = true;
      changes = true;
    }

    if (oldSubroutine == null) {
      if (subroutine != null) {
        subroutines[insn] = subroutine.copy();
        changes = true;
      }
    }
    else if (subroutine != null) {
      changes |= oldSubroutine.merge(subroutine);
    }
    if (changes && !queued[insn]) {
      queued[insn] = true;
      queue[top++] = insn;
    }
  }

  protected void merge(int insn, Subroutine subroutineBeforeJSR, boolean[] access) {
    Subroutine oldSubroutine = subroutines[insn];
    boolean changes = false;

    if (!wasQueued[insn]) {
      wasQueued[insn] = true;
      changes = true;
    }

    if (oldSubroutine != null && subroutineBeforeJSR != null) {
      changes |= oldSubroutine.merge(subroutineBeforeJSR);
    }
    if (changes && !queued[insn]) {
      queued[insn] = true;
      queue[top++] = insn;
    }
  }

  @ApiStatus.Internal
  public interface EdgeCreator {
    void newControlFlowEdge(int insn, int successor);
    void newControlFlowExceptionEdge(int insn, int successor, boolean npe);
  }
}