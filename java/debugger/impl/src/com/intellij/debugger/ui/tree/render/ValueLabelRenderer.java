package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.ValueDescriptor;

/**
 * User: lex
 * Date: Sep 20, 2003
 * Time: 10:12:39 PM
 */
public interface ValueLabelRenderer extends Renderer {
  String calcLabel (ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException;
}
