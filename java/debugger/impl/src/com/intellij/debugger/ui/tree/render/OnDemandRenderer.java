// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.actions.MuteRendererUtils;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.HeadlessValueEvaluationCallback;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeEx;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface OnDemandRenderer extends FullValueEvaluatorProvider {
  @Override
  default @Nullable XFullValueEvaluator getFullValueEvaluator(@NotNull EvaluationContextImpl evaluationContext,
                                                              @NotNull ValueDescriptorImpl valueDescriptor) {
    if (isOnDemand(evaluationContext, valueDescriptor) && !isCalculated(valueDescriptor)) {
      return createFullValueEvaluator(getLinkText());
    }
    return null;
  }

  @Override
  @ApiStatus.Experimental
  default @Nullable XFullValueEvaluator getFullValueEvaluator(
    @NotNull XValueNode valueNode,
    @NotNull EvaluationContextImpl evaluationContext,
    @NotNull ValueDescriptorImpl valueDescriptor
  ) {
    if (isOnDemand(evaluationContext, valueDescriptor) && !isCalculated(valueDescriptor)) {
      return createFullValueEvaluator(valueNode, valueDescriptor, getLinkText());
    }
    return null;
  }

  @Nls String getLinkText();

  default boolean isOnDemand(EvaluationContext evaluationContext, ValueDescriptor valueDescriptor) {
    return isOnDemandForced((DebugProcessImpl)evaluationContext.getDebugProcess());
  }

  default boolean isShowValue(ValueDescriptor valueDescriptor, EvaluationContext evaluationContext) {
    return !isOnDemand(evaluationContext, valueDescriptor) || isCalculated(valueDescriptor);
  }

  static XFullValueEvaluator createFullValueEvaluator(@Nls String text) {
    return new XFullValueEvaluator(text) {
      @Override
      public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
        if (callback instanceof HeadlessValueEvaluationCallback) {
          XValueNodeImpl node = ((HeadlessValueEvaluationCallback)callback).getNode();
          node.clearFullValueEvaluator();
          setCalculated(((JavaValue)node.getValueContainer()).getDescriptor());
          node.getValueContainer().computePresentation(node, XValuePlace.TREE);
        }
        callback.evaluated("");
      }
    }.setShowValuePopup(false);
  }

  @ApiStatus.Experimental
  static XFullValueEvaluator createFullValueEvaluator(
    @NotNull XValueNode valueNode,
    @NotNull ValueDescriptor valueDescriptor,
    @Nls String text
  ) {
    return new XFullValueEvaluator(text) {
      @Override
      public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
        if (valueNode instanceof XValueNodeEx xValueNodeEx) {
          xValueNodeEx.clearFullValueEvaluator();
          setCalculated(valueDescriptor);
          xValueNodeEx.getXValue().computePresentation(valueNode, XValuePlace.TREE);
        }
        callback.evaluated("");
      }
    }.setShowValuePopup(false);
  }

  Key<Boolean> ON_DEMAND_CALCULATED = Key.create("ON_DEMAND_CALCULATED");

  static boolean isCalculated(ValueDescriptor descriptor) {
    return ON_DEMAND_CALCULATED.get(descriptor, false);
  }

  static void setCalculated(ValueDescriptor descriptor) {
    ON_DEMAND_CALCULATED.set(descriptor, true);
  }

  static boolean isOnDemandForced(DebugProcessImpl debugProcess) {
    JavaDebugProcess process = debugProcess.getXdebugProcess();
    return process != null && MuteRendererUtils.isMuted(((XDebugSessionImpl)process.getSession()).getSessionData());
  }
}
