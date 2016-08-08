/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extended version of {@link org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer}.
 * It handles frames <b>and</b> additional data.
 *
 * @author lambdamix
 */
public class AnalyzerExt<V extends Value, Data, MyInterpreter extends Interpreter<V> & InterpreterExt<Data>> extends SubroutineFinder {

  private final MyInterpreter interpreter;

  private Frame<V>[] frames;

  private boolean[] queued;

  private int[] queue;

  private int top;

  public Data[] getData() {
    return data;
  }

  private final Data[] data;

  public AnalyzerExt(final MyInterpreter interpreter, Data[] data, Data startData) {
    this.interpreter = interpreter;
    this.data = data;
    if (data.length > 0) {
      data[0] = startData;
    }
  }

  public Frame<V>[] analyze(final String owner, final MethodNode m) throws AnalyzerException {
    if ((m.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) {
      frames = (Frame<V>[]) new Frame<?>[0];
      return frames;
    }
    final V refV = (V) BasicValue.REFERENCE_VALUE;

    n = m.instructions.size();
    insns = m.instructions;
    handlers = (List<TryCatchBlockNode>[]) new List<?>[n];
    frames = (Frame<V>[]) new Frame<?>[n];
    subroutines = new Subroutine[n];
    queued = new boolean[n];
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

    // initializes the data structures for the control flow analysis
    Frame<V> current = newFrame(m.maxLocals, m.maxStack);
    Frame<V> handler = newFrame(m.maxLocals, m.maxStack);
    current.setReturn(interpreter.newValue(Type.getReturnType(m.desc)));
    Type[] args = Type.getArgumentTypes(m.desc);
    int local = 0;
    if ((m.access & ACC_STATIC) == 0) {
      Type ctype = Type.getObjectType(owner);
      current.setLocal(local++, interpreter.newValue(ctype));
    }
    for (int i = 0; i < args.length; ++i) {
      current.setLocal(local++, interpreter.newValue(args[i]));
      if (args[i].getSize() == 2) {
        current.setLocal(local++, interpreter.newValue(null));
      }
    }
    while (local < m.maxLocals) {
      current.setLocal(local++, interpreter.newValue(null));
    }

    interpreter.init(data[0]);
    merge(0, current, null);

    init(owner, m);

    // control flow analysis
    while (top > 0) {
      int insn = queue[--top];
      Frame<V> f = frames[insn];
      Subroutine subroutine = subroutines[insn];
      queued[insn] = false;

      AbstractInsnNode insnNode = null;
      try {
        insnNode = m.instructions.get(insn);
        int insnOpcode = insnNode.getOpcode();
        int insnType = insnNode.getType();

        if (insnType == AbstractInsnNode.LABEL
            || insnType == AbstractInsnNode.LINE
            || insnType == AbstractInsnNode.FRAME) {
          interpreter.init(data[insn]);
          merge(insn + 1, f, subroutine);
          newControlFlowEdge(insn, insn + 1);
        } else {
          // delta
          interpreter.init(data[insn]);
          current.init(f).execute(insnNode, interpreter);
          subroutine = subroutine == null ? null : subroutine.copy();

          if (insnNode instanceof JumpInsnNode) {
            JumpInsnNode j = (JumpInsnNode) insnNode;
            if (insnOpcode != GOTO && insnOpcode != JSR) {
              merge(insn + 1, current, subroutine);
              newControlFlowEdge(insn, insn + 1);
            }
            int jump = insns.indexOf(j.label);
            if (insnOpcode == JSR) {
              merge(jump, current, new Subroutine(j.label,
                                                  m.maxLocals, j));
            } else {
              merge(jump, current, subroutine);
            }
            newControlFlowEdge(insn, jump);
          } else if (insnNode instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode lsi = (LookupSwitchInsnNode) insnNode;
            int jump = insns.indexOf(lsi.dflt);
            merge(jump, current, subroutine);
            newControlFlowEdge(insn, jump);
            for (int j = 0; j < lsi.labels.size(); ++j) {
              LabelNode label = lsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, current, subroutine);
              newControlFlowEdge(insn, jump);
            }
          } else if (insnNode instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode tsi = (TableSwitchInsnNode) insnNode;
            int jump = insns.indexOf(tsi.dflt);
            merge(jump, current, subroutine);
            newControlFlowEdge(insn, jump);
            for (int j = 0; j < tsi.labels.size(); ++j) {
              LabelNode label = tsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, current, subroutine);
              newControlFlowEdge(insn, jump);
            }
          } else if (insnOpcode == RET) {
            if (subroutine == null) {
              throw new AnalyzerException(insnNode,
                                          "RET instruction outside of a sub routine");
            }
            for (int i = 0; i < subroutine.callers.size(); ++i) {
              JumpInsnNode caller = subroutine.callers.get(i);
              int call = insns.indexOf(caller);
              if (frames[call] != null) {
                merge(call + 1, frames[call], current,
                      subroutines[call], subroutine.access);
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
            merge(insn + 1, current, subroutine);
            newControlFlowEdge(insn, insn + 1);
          }
        }

        List<TryCatchBlockNode> insnHandlers = handlers[insn];
        if (insnHandlers != null) {
          for (int i = 0; i < insnHandlers.size(); ++i) {
            TryCatchBlockNode tcb = insnHandlers.get(i);
            int jump = insns.indexOf(tcb.handler);
            if (newControlFlowExceptionEdge(insn, tcb)) {
              handler.init(f);
              handler.clearStack();
              handler.push(refV);
              merge(jump, handler, subroutine);
            }
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

    return frames;
  }

  public Frame<V>[] getFrames() {
    return frames;
  }

  public List<TryCatchBlockNode> getHandlers(final int insn) {
    return handlers[insn];
  }

  protected void init(String owner, MethodNode m) throws AnalyzerException {
  }

  protected Frame<V> newFrame(final int nLocals, final int nStack) {
    return new Frame<>(nLocals, nStack);
  }

  protected Frame<V> newFrame(final Frame<? extends V> src) {
    return new Frame<>(src);
  }

  protected void newControlFlowEdge(final int insn, final int successor) {
  }

  protected boolean newControlFlowExceptionEdge(final int insn,
                                                final int successor) {
    return true;
  }

  protected boolean newControlFlowExceptionEdge(final int insn,
                                                final TryCatchBlockNode tcb) {
    return newControlFlowExceptionEdge(insn, insns.indexOf(tcb.handler));
  }

  // -------------------------------------------------------------------------

  private void merge(final int insn, final Frame<V> frame,
                     final Subroutine subroutine) throws AnalyzerException {
    Frame<V> oldFrame = frames[insn];
    Subroutine oldSubroutine = subroutines[insn];
    boolean changes;

    if (oldFrame == null) {
      frames[insn] = newFrame(frame);
      changes = true;
    } else {
      changes = oldFrame.merge(frame, interpreter);
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

    // delta
    mergeData(insn, interpreter);
  }

  private void merge(final int insn, final Frame<V> beforeJSR,
                     final Frame<V> afterRET, final Subroutine subroutineBeforeJSR,
                     final boolean[] access) throws AnalyzerException {
    Frame<V> oldFrame = frames[insn];
    Subroutine oldSubroutine = subroutines[insn];
    boolean changes;

    afterRET.merge(beforeJSR, access);

    if (oldFrame == null) {
      frames[insn] = newFrame(afterRET);
      changes = true;
    } else {
      changes = oldFrame.merge(afterRET, interpreter);
    }

    if (oldSubroutine != null && subroutineBeforeJSR != null) {
      changes |= oldSubroutine.merge(subroutineBeforeJSR);
    }
    if (changes && !queued[insn]) {
      queued[insn] = true;
      queue[top++] = insn;
    }

    // delta
    mergeData(insn, interpreter);
  }

  private void mergeData(int insn, MyInterpreter interpreter) {
    boolean changes = false;

    Data oldData = data[insn];
    Data newData = interpreter.getAfterData(insn);

    if (oldData == null) {
      data[insn] = newData;
      changes = true;
    } else if (newData != null) {
      Data mergedData = interpreter.merge(oldData, newData);
      data[insn] = mergedData;
      changes = !oldData.equals(mergedData);
    }

    if (changes && !queued[insn]) {
      queued[insn] = true;
      queue[top++] = insn;
    }
  }
}
