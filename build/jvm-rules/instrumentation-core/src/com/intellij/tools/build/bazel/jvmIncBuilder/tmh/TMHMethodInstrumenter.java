// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.tmh;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeMethodVisitor;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Writes the assertion calls of one method before the first instruction of its code, under a label of their own. A local
 * variable that starts at the first label of the original code starts at that label instead, so a debugger shows the
 * parameters during the assertions.
 */
public final class TMHMethodInstrumenter extends FailSafeMethodVisitor {
  private final List<? extends TMHAssertionGenerator> myGenerators;
  private final int myMethodStartLineNumber;
  private Label myPrologueStart;
  private Label myOriginalFirstLabel;

  /**
   * @param generators the generators of the assertions the method gets, in the order of the calls
   * @param methodStartLineNumber the line number of the assertion calls, or -1 for calls without a line number
   */
  public TMHMethodInstrumenter(int api, MethodVisitor mv, Collection<? extends TMHAssertionGenerator> generators, int methodStartLineNumber) {
    super(api, mv);
    myGenerators = List.copyOf(generators);
    myMethodStartLineNumber = methodStartLineNumber;
  }

  @Override
  public void visitCode() {
    myPrologueStart = new Label();
    mv.visitLabel(myPrologueStart);
    for (TMHAssertionGenerator generator : myGenerators) {
      generator.generateAssertion(mv, myMethodStartLineNumber);
    }
    super.visitCode();
  }

  @Override
  public void visitLabel(Label label) {
    if (myOriginalFirstLabel == null) {
      myOriginalFirstLabel = label;
    }
    super.visitLabel(label);
  }

  @Override
  public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
    Objects.requireNonNull(myOriginalFirstLabel, "no label was visited before the first visitLocalVariable call");
    Objects.requireNonNull(myPrologueStart, "no code was visited before the first visitLocalVariable call");
    Label newStart = start == myOriginalFirstLabel ? myPrologueStart : start;
    super.visitLocalVariable(name, desc, signature, newStart, end, index);
  }
}
