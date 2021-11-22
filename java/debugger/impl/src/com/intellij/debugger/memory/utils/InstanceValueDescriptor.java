// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.utils;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

public class InstanceValueDescriptor extends ValueDescriptorImpl {

  public InstanceValueDescriptor(Project project, Value value) {
    super(project, value);
  }

  @Override
  public String calcValueName() {
    ObjectReference ref = ((ObjectReference) getValue());
    if (ref instanceof ArrayReference) {
      ArrayReference arrayReference = (ArrayReference) ref;
      return NamesUtils.getArrayUniqueName(arrayReference);
    }
    return NamesUtils.getUniqueName(ref);
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return getValue();
  }

  @Override
  public boolean isShowIdLabel() {
    return false;
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext debuggerContext) throws EvaluateException {
    throw new NeedMarkException((ObjectReference)getValue());
  }
}
