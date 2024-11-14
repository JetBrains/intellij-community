// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.PossiblySyncCommand;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.statistics.JavaDebuggerEvaluatorStatisticsCollector;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationOrigin;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LabelRenderer extends ReferenceRenderer implements ValueLabelRenderer, OnDemandRenderer {
  public static final @NonNls String UNIQUE_ID = "LabelRenderer";
  public boolean ON_DEMAND;

  private CachedEvaluator myLabelExpression = createCachedEvaluator();
  private String myPrefix;

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
      return prefix("");
    }

    Value value = descriptor.getValue();
    if (value == null) {
      return prefix("null");
    }

    EvaluationContextImpl evaluationContextImpl = (EvaluationContextImpl)evaluationContext;
    DebugProcessImpl debugProcess = evaluationContextImpl.getDebugProcess();
    evaluationContextImpl.getManagerThread().schedule(new PossiblySyncCommand(evaluationContextImpl.getSuspendContext()) {
      @Override
      public void syncAction(@NotNull SuspendContextImpl suspendContext) {
        ExpressionEvaluator evaluator = null;
        try {
          evaluator = myLabelExpression.getEvaluator(debugProcess.getProject());

          if (!debugProcess.isAttached()) {
            throw EvaluateExceptionUtil.PROCESS_EXITED;
          }
          EvaluationContext thisEvaluationContext = evaluationContext.createEvaluationContext(value);
          Value labelValue = evaluator.evaluate(thisEvaluationContext);
          JavaDebuggerEvaluatorStatisticsCollector.logEvaluationResult(debugProcess.getProject(), evaluator, true, XEvaluationOrigin.RENDERER);
          String result = StringUtil.notNullize(DebuggerUtils.getValueAsString(thisEvaluationContext, labelValue));
          descriptor.setValueLabel(prefix(result));
        }
        catch (EvaluateException ex) {
          JavaDebuggerEvaluatorStatisticsCollector.logEvaluationResult(debugProcess.getProject(), evaluator, false, XEvaluationOrigin.RENDERER);
          descriptor.setValueLabelFailed(
            new EvaluateException(JavaDebuggerBundle.message("error.unable.to.evaluate.expression") + " " + ex.getMessage(), ex));
        }
        labelListener.labelChanged();
      }
    });
    return XDebuggerUIConstants.getCollectingDataMessage();
  }

  private String prefix(String result) {
    return myPrefix != null ? myPrefix + result : result;
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

  public void setPrefix(@Nullable String prefix) {
    myPrefix = prefix;
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
