// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;

import java.util.List;

/**
 * Specialized lite version of {@link FramelessAnalyzer}.
 * No processing of Subroutines. May be used for methods without JSR/RET instructions.
 */
public final class LiteFramelessAnalyzer extends FramelessAnalyzer {
  public LiteFramelessAnalyzer(EdgeCreator creator) {super(creator);}

  @Override
  protected void findSubroutine(int insn, Subroutine sub, List<AbstractInsnNode> calls) { }

  @Override
  protected void merge(final int insn, final Subroutine subroutine) {
    if (!wasQueued[insn]) {
      wasQueued[insn] = true;
      if (!queued[insn]) {
        queued[insn] = true;
        queue[top++] = insn;
      }
    }
  }

  @Override
  protected void merge(int insn, Subroutine subroutineBeforeJSR, boolean[] access) {
    if (!wasQueued[insn]) {
      wasQueued[insn] = true;
      if (!queued[insn]) {
        queued[insn] = true;
        queue[top++] = insn;
      }
    }
  }
}