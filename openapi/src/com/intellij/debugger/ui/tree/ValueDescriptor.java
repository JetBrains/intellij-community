package com.intellij.debugger.ui.tree;

import com.sun.jdi.Value;
import com.sun.jdi.PrimitiveValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.DebuggerContext;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface ValueDescriptor extends NodeDescriptor{
  PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException;

  public Value getValue();

  String setValueLabel(String customLabel);

  String setValueLabelFailed(EvaluateException e);

  boolean isArray();
  boolean isLvalue();
  boolean isNull();
  boolean isPrimitive();
}
