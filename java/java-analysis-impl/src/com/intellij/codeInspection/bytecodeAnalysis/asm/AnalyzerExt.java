// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extended version of {@link Analyzer}.
 * It handles frames <b>and</b> additional data.
 *
 * @author lambdamix
 */
public class AnalyzerExt<V extends Value, Data, MyInterpreter extends Interpreter<V> & InterpreterExt<Data>> extends SubroutineFinder {
  private final MyInterpreter interpreter;
  private final Data[] data;
  private Frame<V>[] frames;
  private boolean[] queued;
  private int[] queue;
  private int top;

  public AnalyzerExt(MyInterpreter interpreter, Data[] data, Data startData) {
    this.interpreter = interpreter;
    this.data = data;
    if (data.length > 0) {
      data[0] = startData;
    }
  }

  public Data[] getData() {
    return data;
  }

  public Frame<V>[] analyze(String owner, MethodNode m) throws AnalyzerException {
    if ((m.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) {
      frames = ASMUtils.newFrameArray(0);
      return frames;
    }

    @SuppressWarnings("unchecked") V refV = (V)BasicValue.REFERENCE_VALUE;

    n = m.instructions.size();
    insns = m.instructions;
    handlers = ASMUtils.newListArray(n);
    frames = ASMUtils.newFrameArray(n);
    subroutines = new Subroutine[n];
    queued = new boolean[n];
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

    // initializes the data structures for the control flow analysis
    Frame<V> current = newFrame(m.maxLocals, m.maxStack);
    Frame<V> handler = newFrame(m.maxLocals, m.maxStack);
    current.setReturn(interpreter.newReturnTypeValue(Type.getReturnType(m.desc)));
    Type[] args = Type.getArgumentTypes(m.desc);
    int local = 0;
    boolean isInstanceMethod = (m.access & ACC_STATIC) == 0;
    if (isInstanceMethod) {
      Type ctype = Type.getObjectType(owner);
      current.setLocal(local, interpreter.newParameterValue(true, local, ctype));
      local++;
    }
    for (Type arg : args) {
      current.setLocal(local, interpreter.newParameterValue(isInstanceMethod, local, arg));
      local++;
      if (arg.getSize() == 2) {
        current.setLocal(local, interpreter.newEmptyValue(local));
        local++;
      }
    }
    while (local < m.maxLocals) {
      current.setLocal(local, interpreter.newEmptyValue(local));
      local++;
    }

    interpreter.init(data[0]);
    merge(0, current, null);

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
        }
        else {
          // delta
          interpreter.init(data[insn]);
          current.init(f).execute(insnNode, interpreter);
          subroutine = subroutine == null ? null : subroutine.copy();

          if (insnNode instanceof JumpInsnNode) {
            JumpInsnNode j = (JumpInsnNode)insnNode;
            if (insnOpcode != GOTO && insnOpcode != JSR) {
              merge(insn + 1, current, subroutine);
            }
            int jump = insns.indexOf(j.label);
            if (insnOpcode == JSR) {
              merge(jump, current, new Subroutine(j.label,
                                                  m.maxLocals, j));
            }
            else {
              merge(jump, current, subroutine);
            }
          }
          else if (insnNode instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode lsi = (LookupSwitchInsnNode)insnNode;
            int jump = insns.indexOf(lsi.dflt);
            merge(jump, current, subroutine);
            for (LabelNode label : lsi.labels) {
              jump = insns.indexOf(label);
              merge(jump, current, subroutine);
            }
          }
          else if (insnNode instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode tsi = (TableSwitchInsnNode)insnNode;
            int jump = insns.indexOf(tsi.dflt);
            merge(jump, current, subroutine);
            for (LabelNode label : tsi.labels) {
              jump = insns.indexOf(label);
              merge(jump, current, subroutine);
            }
          }
          else if (insnOpcode == RET) {
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
            merge(insn + 1, current, subroutine);
          }
        }

        List<TryCatchBlockNode> insnHandlers = handlers[insn];
        if (insnHandlers != null) {
          for (TryCatchBlockNode tcb : insnHandlers) {
            int jump = insns.indexOf(tcb.handler);
            handler.init(f);
            handler.clearStack();
            handler.push(refV);
            merge(jump, handler, subroutine);
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

    return frames;
  }

  public Frame<V>[] getFrames() {
    return frames;
  }

  protected Frame<V> newFrame(int nLocals, int nStack) {
    return new Frame<>(nLocals, nStack);
  }

  protected Frame<V> newFrame(Frame<? extends V> src) {
    return new Frame<>(src);
  }

  // -------------------------------------------------------------------------

  private void merge(int insn, Frame<V> frame, Subroutine subroutine) throws AnalyzerException {
    Frame<V> oldFrame = frames[insn];
    Subroutine oldSubroutine = subroutines[insn];
    boolean changes;

    if (oldFrame == null) {
      frames[insn] = newFrame(frame);
      changes = true;
    }
    else {
      changes = oldFrame.merge(frame, interpreter);
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

    // delta
    mergeData(insn, interpreter);
  }

  private void merge(int insn, Frame<V> beforeJSR,
                     Frame<V> afterRET, Subroutine subroutineBeforeJSR,
                     boolean[] access) throws AnalyzerException {
    Frame<V> oldFrame = frames[insn];
    Subroutine oldSubroutine = subroutines[insn];
    boolean changes;

    afterRET.merge(beforeJSR, access);

    if (oldFrame == null) {
      frames[insn] = newFrame(afterRET);
      changes = true;
    }
    else {
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
    }
    else if (newData != null) {
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