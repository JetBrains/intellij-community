/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Specialized version of {@link org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer}.
 * Calculation of fix-point of frames is removed, since frames are not needed to build control flow graph.
 * So, the main point here is handling of subroutines (jsr) and try-catch-finally blocks.
 */
public class FramelessAnalyzer extends SubroutineFinder {
  protected boolean[] wasQueued;
  protected boolean[] queued;
  protected int[] queue;
  protected int top;

  public void analyze(final MethodNode m) throws AnalyzerException {
    n = m.instructions.size();
    if ((m.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0 || n == 0) {
      return;
    }
    insns = m.instructions;
    handlers = (List<TryCatchBlockNode>[]) new List<?>[n];
    subroutines = new Subroutine[n];
    queued = new boolean[n];
    wasQueued = new boolean[n];
    queue = new int[n];
    top = 0;

    // computes exception handlers for each instruction
    for (int i = 0; i < m.tryCatchBlocks.size(); ++i) {
      TryCatchBlockNode tcb = m.tryCatchBlocks.get(i);
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
      JumpInsnNode jsr = (JumpInsnNode) subroutineCalls.remove(0);
      Subroutine sub = subroutineHeads.get(jsr.label);
      if (sub == null) {
        sub = new Subroutine(jsr.label, m.maxLocals, jsr);
        subroutineHeads.put(jsr.label, sub);
        findSubroutine(insns.indexOf(jsr.label), sub, subroutineCalls);
      } else {
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
          newControlFlowEdge(insn, insn + 1);
        } else {
          subroutine = subroutine == null ? null : subroutine.copy();

          if (insnNode instanceof JumpInsnNode) {
            JumpInsnNode j = (JumpInsnNode) insnNode;
            if (insnOpcode != GOTO && insnOpcode != JSR) {
              merge(insn + 1, subroutine);
              newControlFlowEdge(insn, insn + 1);
            }
            int jump = insns.indexOf(j.label);
            if (insnOpcode == JSR) {
              merge(jump, new Subroutine(j.label, m.maxLocals, j));
            } else {
              merge(jump, subroutine);
            }
            newControlFlowEdge(insn, jump);
          } else if (insnNode instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode lsi = (LookupSwitchInsnNode) insnNode;
            int jump = insns.indexOf(lsi.dflt);
            merge(jump, subroutine);
            newControlFlowEdge(insn, jump);
            for (int j = 0; j < lsi.labels.size(); ++j) {
              LabelNode label = lsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, subroutine);
              newControlFlowEdge(insn, jump);
            }
          } else if (insnNode instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode tsi = (TableSwitchInsnNode) insnNode;
            int jump = insns.indexOf(tsi.dflt);
            merge(jump, subroutine);
            newControlFlowEdge(insn, jump);
            for (int j = 0; j < tsi.labels.size(); ++j) {
              LabelNode label = tsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, subroutine);
              newControlFlowEdge(insn, jump);
            }
          } else if (insnOpcode == RET) {
            if (subroutine == null) {
              throw new AnalyzerException(insnNode, "RET instruction outside of a sub routine");
            }
            for (int i = 0; i < subroutine.callers.size(); ++i) {
              JumpInsnNode caller = subroutine.callers.get(i);
              int call = insns.indexOf(caller);
              if (wasQueued[call]) {
                merge(call + 1, subroutines[call], subroutine.access);
                newControlFlowEdge(insn, call + 1);
              }
            }
          } else if (insnOpcode != ATHROW && (insnOpcode < IRETURN || insnOpcode > RETURN)) {
            if (subroutine != null) {
              if (insnNode instanceof VarInsnNode) {
                int var = ((VarInsnNode) insnNode).var;
                subroutine.access[var] = true;
                if (insnOpcode == LLOAD || insnOpcode == DLOAD
                    || insnOpcode == LSTORE
                    || insnOpcode == DSTORE) {
                  subroutine.access[var + 1] = true;
                }
              } else if (insnNode instanceof IincInsnNode) {
                int var = ((IincInsnNode) insnNode).var;
                subroutine.access[var] = true;
              }
            }
            merge(insn + 1, subroutine);
            newControlFlowEdge(insn, insn + 1);
          }
        }

        List<TryCatchBlockNode> insnHandlers = handlers[insn];
        if (insnHandlers != null) {
          for (TryCatchBlockNode tcb : insnHandlers) {
            newControlFlowExceptionEdge(insn, tcb);
            merge(insns.indexOf(tcb.handler), subroutine);
          }
        }
      } catch (AnalyzerException e) {
        throw new AnalyzerException(e.node, "Error at instruction "
                                            + insn + ": " + e.getMessage(), e);
      } catch (Exception e) {
        throw new AnalyzerException(insnNode, "Error at instruction "
                                              + insn + ": " + e.getMessage(), e);
      }
    }
  }

  protected void newControlFlowEdge(final int insn, final int successor) {}

  protected boolean newControlFlowExceptionEdge(final int insn, final int successor) {
    return true;
  }

  protected boolean newControlFlowExceptionEdge(final int insn, final TryCatchBlockNode tcb) {
    return newControlFlowExceptionEdge(insn, insns.indexOf(tcb.handler));
  }

  // -------------------------------------------------------------------------

  protected void merge(final int insn, @Nullable final Subroutine subroutine) throws AnalyzerException {
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
    } else {
      if (subroutine != null) {
        changes |= oldSubroutine.merge(subroutine);
      }
    }
    if (changes && !queued[insn]) {
      queued[insn] = true;
      queue[top++] = insn;
    }
  }

  protected void merge(final int insn, final Subroutine subroutineBeforeJSR, final boolean[] access) throws AnalyzerException {
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
}
