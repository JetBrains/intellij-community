// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.xdebugger.frame.XValueNode;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class SimpleReferringObject implements ReferringObject {
  private final @NotNull ObjectReference myReference;

  public SimpleReferringObject(@NotNull ObjectReference reference) {
    myReference = reference;
  }

  @Override
  public @NotNull ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new ValueDescriptorImpl(project, myReference) {
      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
        return getValue();
      }

      @Override
      public String getName() {
        return JavaDebuggerBundle.message("ref");
      }


      @Override
      public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws NeedMarkException {
        throw new NeedMarkException(myReference);
      }
    };
  }

  @Override
  public @Nullable String getNodeName(int order) {
    return "Referrer " + order;
  }

  @Override
  public @NotNull ObjectReference getReference() {
    return myReference;
  }

  @Override
  public @NotNull Function<XValueNode, XValueNode> getNodeCustomizer() {
    return Function.identity();
  }
}
