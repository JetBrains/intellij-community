// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

public class MethodReturnValueDescriptorImpl extends ValueDescriptorImpl {
  private final Method myMethod;

  public MethodReturnValueDescriptorImpl(Project project, @NotNull Method method, Value value) {
    super(project, value);
    myMethod = method;
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return getValue();
  }

  public @NotNull Method getMethod() {
    return myMethod;
  }

  @Override
  public String getName() {
    return NodeRendererSettings.getInstance().getClassRenderer().renderTypeName(myMethod.declaringType().name()) + "." +
           DebuggerUtilsEx.methodNameWithArguments(myMethod);
  }

  @Override
  public Type getType() {
    Type type = super.getType();
    if (type == null) {
      try {
        type = myMethod.returnType();
      }
      catch (ClassNotLoadedException ignored) {
      }
    }
    return type;
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    Value value = getValue();
    if (value instanceof ObjectReference) {
      throw new NeedMarkException((ObjectReference)value);
    }
    return null;
  }

  @Override
  public boolean canSetValue() {
    return false;
  }
}
