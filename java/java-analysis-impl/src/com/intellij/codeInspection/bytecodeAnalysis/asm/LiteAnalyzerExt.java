// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended version of {@link LiteAnalyzer}.
 * It handles frames <b>and</b> additional data.
 *
 * @author lambdamix
 */
public class LiteAnalyzerExt<V extends Value, Data, MyInterpreter extends Interpreter<V> & InterpreterExt<Data>> implements Opcodes {
  private final MyInterpreter interpreter;
  private final Data[] data;
  private Frame<V>[] frames;
  private boolean[] queued;
  private int[] queue;
  private int top;

  public LiteAnalyzerExt(MyInterpreter interpreter, Data[] data, Data startData) {
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

    int n = m.instructions.size();
    InsnList insns = m.instructions;
    List<TryCatchBlockNode>[] handlers = ASMUtils.newListArray(n);
    frames = ASMUtils.newFrameArray(n);
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
    merge(0, current);

    // control flow analysis
    while (top > 0) {
      int insn = queue[--top];
      Frame<V> f = frames[insn];
      queued[insn] = false;

      AbstractInsnNode insnNode = null;
      try {
        insnNode = m.instructions.get(insn);
        int insnOpcode = insnNode.getOpcode();
        int insnType = insnNode.getType();

        if (insnType == AbstractInsnNode.LABEL || insnType == AbstractInsnNode.LINE || insnType == AbstractInsnNode.FRAME) {
          interpreter.init(data[insn]);
          merge(insn + 1, f);
        }
        else {
          // delta
          interpreter.init(data[insn]);
          current.init(f).execute(insnNode, interpreter);

          if (insnNode instanceof JumpInsnNode) {
            JumpInsnNode j = (JumpInsnNode)insnNode;
            if (insnOpcode != GOTO && insnOpcode != JSR) {
              merge(insn + 1, current);
            }
            int jump = insns.indexOf(j.label);
            merge(jump, current);
          }
          else if (insnNode instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode lsi = (LookupSwitchInsnNode)insnNode;
            int jump = insns.indexOf(lsi.dflt);
            merge(jump, current);
            for (int j = 0; j < lsi.labels.size(); ++j) {
              LabelNode label = lsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, current);
            }
          }
          else if (insnNode instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode tsi = (TableSwitchInsnNode)insnNode;
            int jump = insns.indexOf(tsi.dflt);
            merge(jump, current);
            for (int j = 0; j < tsi.labels.size(); ++j) {
              LabelNode label = tsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, current);
            }
          }
          else if (insnOpcode != ATHROW
                   && (insnOpcode < IRETURN || insnOpcode > RETURN)) {
            merge(insn + 1, current);
          }
        }

        List<TryCatchBlockNode> insnHandlers = handlers[insn];
        if (insnHandlers != null) {
          for (TryCatchBlockNode tcb : insnHandlers) {
            int jump = insns.indexOf(tcb.handler);
            handler.init(f);
            handler.clearStack();
            handler.push(refV);
            merge(jump, handler);
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

  private void merge(int insn, Frame<V> frame) throws AnalyzerException {
    Frame<V> oldFrame = frames[insn];
    boolean changes;

    if (oldFrame == null) {
      frames[insn] = newFrame(frame);
      changes = true;
    }
    else {
      changes = oldFrame.merge(frame, interpreter);
    }

    Data oldData = data[insn];
    Data newData = interpreter.getAfterData(insn);

    if (oldData == null) {
      data[insn] = newData;
      changes = true;
    }
    else if (newData != null) {
      Data mergedData = interpreter.merge(oldData, newData);
      data[insn] = mergedData;
      changes |= !oldData.equals(mergedData);
    }

    if (changes && !queued[insn]) {
      queued[insn] = true;
      queue[top++] = insn;
    }
  }
}