// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerContext;
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
  @NotNull private final ObjectReference myReference;

  public SimpleReferringObject(@NotNull ObjectReference reference) {
    myReference = reference;
  }

  @NotNull
  @Override
  public ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new ValueDescriptorImpl(project, myReference) {
      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
        return getValue();
      }

      @Override
      public String getName() {
        return "Ref";
      }


      @Override
      public PsiExpression getDescriptorEvaluation(DebuggerContext context) {
        return null;
      }
    };
  }

  @Nullable
  @Override
  public String getNodeName(int order) {
    return "Referrer " + order;
  }

  @NotNull
  @Override
  public ObjectReference getReference() {
    return myReference;
  }

  @NotNull
  @Override
  public Function<XValueNode, XValueNode> getNodeCustomizer() {
    return Function.identity();
  }
}
