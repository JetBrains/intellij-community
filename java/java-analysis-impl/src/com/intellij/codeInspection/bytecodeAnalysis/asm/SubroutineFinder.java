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
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.List;

abstract class SubroutineFinder implements Opcodes {
  InsnList insns;
  List<TryCatchBlockNode>[] handlers;
  Subroutine[] subroutines;
  int n;
  void findSubroutine(int insn, final Subroutine sub,
                              final List<AbstractInsnNode> calls) throws AnalyzerException {
    while (true) {
      if (insn < 0 || insn >= n) {
        throw new AnalyzerException(null,
                                    "Execution can fall off end of the code");
      }
      if (subroutines[insn] != null) {
        return;
      }
      subroutines[insn] = sub.copy();
      AbstractInsnNode node = insns.get(insn);

      // calls findSubroutine recursively on normal successors
      if (node instanceof JumpInsnNode) {
        if (node.getOpcode() == JSR) {
          // do not follow a JSR, it leads to another subroutine!
          calls.add(node);
        } else {
          JumpInsnNode jnode = (JumpInsnNode) node;
          findSubroutine(insns.indexOf(jnode.label), sub, calls);
        }
      } else if (node instanceof TableSwitchInsnNode) {
        TableSwitchInsnNode tsnode = (TableSwitchInsnNode) node;
        findSubroutine(insns.indexOf(tsnode.dflt), sub, calls);
        for (int i = tsnode.labels.size() - 1; i >= 0; --i) {
          LabelNode l = tsnode.labels.get(i);
          findSubroutine(insns.indexOf(l), sub, calls);
        }
      } else if (node instanceof LookupSwitchInsnNode) {
        LookupSwitchInsnNode lsnode = (LookupSwitchInsnNode) node;
        findSubroutine(insns.indexOf(lsnode.dflt), sub, calls);
        for (int i = lsnode.labels.size() - 1; i >= 0; --i) {
          LabelNode l = lsnode.labels.get(i);
          findSubroutine(insns.indexOf(l), sub, calls);
        }
      }

      // calls findSubroutine recursively on exception handler successors
      List<TryCatchBlockNode> insnHandlers = handlers[insn];
      if (insnHandlers != null) {
        for (int i = 0; i < insnHandlers.size(); ++i) {
          TryCatchBlockNode tcb = insnHandlers.get(i);
          findSubroutine(insns.indexOf(tcb.handler), sub, calls);
        }
      }

      // if insn does not falls through to the next instruction, return.
      switch (node.getOpcode()) {
        case GOTO:
        case RET:
        case TABLESWITCH:
        case LOOKUPSWITCH:
        case IRETURN:
        case LRETURN:
        case FRETURN:
        case DRETURN:
        case ARETURN:
        case RETURN:
        case ATHROW:
          return;
      }
      insn++;
    }
  }
}
