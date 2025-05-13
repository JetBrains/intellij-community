// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.List;

@ApiStatus.Internal
public abstract class SubroutineFinder implements Opcodes {
  InsnList insns;
  List<TryCatchBlockNode>[] handlers;
  Subroutine[] subroutines;
  int n;

  void findSubroutine(int insn, Subroutine sub, List<AbstractInsnNode> calls) throws AnalyzerException {
    while (true) {
      if (insn < 0 || insn >= n) {
        throw new AnalyzerException(null, "Execution can fall off end of the code");
      }
      if (subroutines[insn] != null) {
        return;
      }
      subroutines[insn] = sub.copy();
      AbstractInsnNode node = insns.get(insn);

      // calls findSubroutine recursively on normal successors
      if (node instanceof JumpInsnNode jNode) {
        if (node.getOpcode() == JSR) {
          // do not follow a JSR, it leads to another subroutine!
          calls.add(node);
        }
        else {
          findSubroutine(insns.indexOf(jNode.label), sub, calls);
        }
      }
      else if (node instanceof TableSwitchInsnNode tsNode) {
        findSubroutine(insns.indexOf(tsNode.dflt), sub, calls);
        for (int i = tsNode.labels.size() - 1; i >= 0; --i) {
          LabelNode l = tsNode.labels.get(i);
          findSubroutine(insns.indexOf(l), sub, calls);
        }
      }
      else if (node instanceof LookupSwitchInsnNode lsNode) {
        findSubroutine(insns.indexOf(lsNode.dflt), sub, calls);
        for (int i = lsNode.labels.size() - 1; i >= 0; --i) {
          LabelNode l = lsNode.labels.get(i);
          findSubroutine(insns.indexOf(l), sub, calls);
        }
      }

      // calls findSubroutine recursively on exception handler successors
      List<TryCatchBlockNode> insnHandlers = handlers[insn];
      if (insnHandlers != null) {
        for (TryCatchBlockNode tcb : insnHandlers) {
          findSubroutine(insns.indexOf(tcb.handler), sub, calls);
        }
      }

      // if insn does not falls through to the next instruction, return.
      switch (node.getOpcode()) {
        case GOTO, RET, TABLESWITCH, LOOKUPSWITCH, IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN, ATHROW -> {
          return;
        }
      }
      insn++;
    }
  }
}