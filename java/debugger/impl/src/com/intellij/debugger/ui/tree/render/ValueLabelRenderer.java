// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ValueLabelRenderer extends Renderer, ValueIconRenderer {
  String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException;

  @Override
  @Nullable
  default Icon calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    return null;
  }
}
