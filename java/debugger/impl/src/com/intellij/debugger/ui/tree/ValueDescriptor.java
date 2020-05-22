// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ValueDescriptor extends NodeDescriptor{
  PsiElement getDescriptorEvaluation(DebuggerContext context) throws EvaluateException;

  Value getValue();

  @Nullable
  default Type getType() {
    Value value = getValue();
    return value != null ? value.type() : null;
  }

  void setValueLabel(String label);

  void setIdLabel(String idLabel);

  String setValueLabelFailed(EvaluateException e);

  Icon setValueIcon(Icon icon);

  boolean isArray();
  boolean isLvalue();
  boolean isNull();
  boolean isPrimitive();
  boolean isString();
  
  @Nullable
  ValueMarkup getMarkup(final DebugProcess debugProcess);
  
  void setMarkup(final DebugProcess debugProcess, @Nullable ValueMarkup markup);
}
