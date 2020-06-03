// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LabelRenderer extends ReferenceRenderer implements ValueLabelRenderer, OnDemandRenderer {
  public static final @NonNls String UNIQUE_ID = "LabelRenderer";
  public boolean ON_DEMAND;

  private CachedEvaluator myLabelExpression = createCachedEvaluator();

  public LabelRenderer() {
    super();
  }

  @Override
  public String getUniqueId() {
    return UNIQUE_ID;
  }

  @Override
  public LabelRenderer clone() {
    LabelRenderer clone = (LabelRenderer)super.clone();
    clone.myLabelExpression = createCachedEvaluator();
    clone.setLabelExpression(getLabelExpression());
    return clone;
  }

  @Override
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)
    throws EvaluateException {

    if (!isShowValue(descriptor, evaluationContext)) {
      return "";
    }

    final Value value = descriptor.getValue();

    String result;
    final DebugProcess debugProcess = evaluationContext.getDebugProcess();
    if (value != null) {
      try {
        final ExpressionEvaluator evaluator = myLabelExpression.getEvaluator(debugProcess.getProject());

        if(!debugProcess.isAttached()) {
          throw EvaluateExceptionUtil.PROCESS_EXITED;
        }
        EvaluationContext thisEvaluationContext = evaluationContext.createEvaluationContext(value);
        Value labelValue = evaluator.evaluate(thisEvaluationContext);
        result = DebuggerUtils.getValueAsString(thisEvaluationContext, labelValue);
      }
      catch (final EvaluateException ex) {
        throw new EvaluateException(JavaDebuggerBundle.message("error.unable.to.evaluate.expression") + " " + ex.getMessage(), ex);
      }
    }
    else {
      result = "null";
    }
    return result;
  }

  @NotNull
  @Override
  public String getLinkText() {
    return "… " + getLabelExpression().getText();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
    TextWithImports labelExpression = DebuggerUtils.getInstance().readTextWithImports(element, "LABEL_EXPRESSION");
    if (labelExpression != null) {
      setLabelExpression(labelExpression);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    DebuggerUtils.getInstance().writeTextWithImports(element, "LABEL_EXPRESSION", getLabelExpression());
  }

  public TextWithImports getLabelExpression() {
    return myLabelExpression.getReferenceExpression();
  }

  public void setLabelExpression(TextWithImports expression) {
    myLabelExpression.setReferenceExpression(expression);
  }

  @Override
  public boolean isOnDemand(EvaluationContext evaluationContext, ValueDescriptor valueDescriptor) {
    return ON_DEMAND || OnDemandRenderer.super.isOnDemand(evaluationContext, valueDescriptor);
  }

  public boolean isOnDemand() {
    return ON_DEMAND;
  }

  public void setOnDemand(boolean value) {
    ON_DEMAND = value;
  }
}
