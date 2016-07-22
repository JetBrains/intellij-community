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

import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame;
import org.jetbrains.org.objectweb.asm.tree.analysis.Interpreter;
import org.jetbrains.org.objectweb.asm.tree.analysis.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Specialized lite version of {@link org.objectweb.asm.tree.analysis.Analyzer}.
 * No processing of Subroutines. May be used for methods without JSR/RET instructions.
 *
 * @author lambdamix
 */
public class LiteAnalyzer<V extends Value> implements Opcodes {

  private final Interpreter<V> interpreter;

  private Frame<V>[] frames;

  private boolean[] queued;

  private int[] queue;

  private int top;

  public LiteAnalyzer(final Interpreter<V> interpreter) {
    this.interpreter = interpreter;
  }

  public Frame<V>[] analyze(final String owner, final MethodNode m) throws AnalyzerException {
    if ((m.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0 || m.instructions.size() == 0) {
      frames = (Frame<V>[]) new Frame<?>[0];
      return frames;
    }
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
    Frame<V> current = new Frame<>(m.maxLocals, m.maxStack);
    Frame<V> handler = new Frame<>(m.maxLocals, m.maxStack);
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
          merge(insn + 1, f);
        } else {
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
          } else if (insnOpcode != ATHROW && (insnOpcode < IRETURN || insnOpcode > RETURN)) {
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
            handler.push(interpreter.newValue(ASMUtils.THROWABLE_TYPE));
            merge(jump, handler);
          }
        }
      } catch (AnalyzerException e) {
        throw new AnalyzerException(e.node, "Error at instruction "  + insn + ": " + e.getMessage(), e);
      } catch (Exception e) {
        throw new AnalyzerException(insnNode, "Error at instruction " + insn + ": " + e.getMessage(), e);
      }
    }

    return frames;
  }

  public Frame<V>[] getFrames() {
    return frames;
  }

  private void merge(final int insn, final Frame<V> frame) throws AnalyzerException {
    Frame<V> oldFrame = frames[insn];
    boolean changes;

    if (oldFrame == null) {
      frames[insn] = new Frame<>(frame);
      changes = true;
    } else {
      changes = oldFrame.merge(frame, interpreter);
    }
    if (changes && !queued[insn]) {
      queued[insn] = true;
      queue[top++] = insn;
    }
  }

}

