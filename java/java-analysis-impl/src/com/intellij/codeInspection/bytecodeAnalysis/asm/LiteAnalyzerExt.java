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

import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended version of {@link com.intellij.codeInspection.bytecodeAnalysis.asm.LiteAnalyzer}.
 * It handles frames <b>and</b> additional data.
 *
 * @author lambdamix
 */
public class LiteAnalyzerExt<V extends Value, Data, MyInterpreter extends Interpreter<V> & InterpreterExt<Data>> implements Opcodes {

  private final MyInterpreter interpreter;
  private Frame<V>[] frames;
  private boolean[] queued;
  private int[] queue;
  private int top;

  public Data[] getData() {
    return data;
  }

  private final Data[] data;

  public LiteAnalyzerExt(final MyInterpreter interpreter, Data[] data, Data startData) {
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

    int n = m.instructions.size();
    InsnList insns = m.instructions;
    List<TryCatchBlockNode>[] handlers = (List<TryCatchBlockNode>[]) new List<?>[n];
    frames = (Frame<V>[]) new Frame<?>[n];
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
        } else {
          // delta
          interpreter.init(data[insn]);
          current.init(f).execute(insnNode, interpreter);

          if (insnNode instanceof JumpInsnNode) {
            JumpInsnNode j = (JumpInsnNode) insnNode;
            if (insnOpcode != GOTO && insnOpcode != JSR) {
              merge(insn + 1, current);
            }
            int jump = insns.indexOf(j.label);
            merge(jump, current);
          } else if (insnNode instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode lsi = (LookupSwitchInsnNode) insnNode;
            int jump = insns.indexOf(lsi.dflt);
            merge(jump, current);
            for (int j = 0; j < lsi.labels.size(); ++j) {
              LabelNode label = lsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, current);
            }
          } else if (insnNode instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode tsi = (TableSwitchInsnNode) insnNode;
            int jump = insns.indexOf(tsi.dflt);
            merge(jump, current);
            for (int j = 0; j < tsi.labels.size(); ++j) {
              LabelNode label = tsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, current);
            }
          } else if (insnOpcode != ATHROW
                     && (insnOpcode < IRETURN || insnOpcode > RETURN)) {
            merge(insn + 1, current);
          }
        }

        List<TryCatchBlockNode> insnHandlers = handlers[insn];
        if (insnHandlers != null) {
          for (int i = 0; i < insnHandlers.size(); ++i) {
            TryCatchBlockNode tcb = insnHandlers.get(i);
            int jump = insns.indexOf(tcb.handler);
            handler.init(f);
            handler.clearStack();
            handler.push(refV);
            merge(jump, handler);
          }
        }
      } catch (AnalyzerException e) {
        throw new AnalyzerException(e.node, "Error at instruction " + insn + ": " + e.getMessage(), e);
      } catch (Exception e) {
        throw new AnalyzerException(insnNode, "Error at instruction " + insn + ": " + e.getMessage(), e);
      }
    }

    return frames;
  }

  public Frame<V>[] getFrames() {
    return frames;
  }

  protected Frame<V> newFrame(final int nLocals, final int nStack) {
    return new Frame<>(nLocals, nStack);
  }

  protected Frame<V> newFrame(final Frame<? extends V> src) {
    return new Frame<>(src);
  }

  // -------------------------------------------------------------------------

  private void merge(final int insn, final Frame<V> frame) throws AnalyzerException {
    Frame<V> oldFrame = frames[insn];
    boolean changes;

    if (oldFrame == null) {
      frames[insn] = newFrame(frame);
      changes = true;
    } else {
      changes = oldFrame.merge(frame, interpreter);
    }

    Data oldData = data[insn];
    Data newData = interpreter.getAfterData(insn);

    if (oldData == null) {
      data[insn] = newData;
      changes = true;
    } else if (newData != null) {
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
