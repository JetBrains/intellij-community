// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.registry.Registry;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.ImageIcon;

@ApiStatus.Internal
public class IconObjectRenderer extends AbstractImageRenderer {
  @Override
  protected String getName() {
    return "Icon";
  }

  @Override
  protected String getClassName() {
    return "javax.swing.Icon";
  }

  @Override
  protected ValueIconRenderer getIconRenderer() {
    return (descriptor, evaluationContext, listener) -> {
      EvaluationContextImpl evalContext = ((EvaluationContextImpl)evaluationContext);
      DebugProcessImpl debugProcess = evalContext.getDebugProcess();

      if (!Registry.is("debugger.auto.fetch.icons") || DebuggerUtilsImpl.isRemote(debugProcess)) return null;

      ((EvaluationContextImpl)evaluationContext).getManagerThread().schedule(new SuspendContextCommandImpl(evalContext.getSuspendContext()) {
        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) {
          descriptor.setValueIcon(getPreviewIcon(evalContext, descriptor.getValue()));
          listener.labelChanged();
        }
      });
      return null;
    };
  }

  protected @Nullable ImageIcon getPreviewIcon(@NotNull EvaluationContextImpl evaluationContext, @NotNull Value obj) {
    String getterName = AllIcons.Debugger.Value.getIconHeight() <= 16 ? "iconToBytesPreviewNormal" : "iconToBytesPreviewRetina";
    return getImageIcon(evaluationContext, obj, getterName);
  }

  @Override
  protected FullValueEvaluatorProvider getFullValueEvaluatorProvider() {
    return (evaluationContext, valueDescriptor) -> {
      return createImagePopupEvaluator(JavaDebuggerBundle.message("message.node.show.icon"), evaluationContext,
                                       valueDescriptor.getValue());
    };
  }

  @Override
  protected byte @Nullable [] getImageBytes(@NotNull EvaluationContextImpl evaluationContext, Value obj) {
    return getImageBytesFromHelper(evaluationContext, obj, "iconToBytes");
  }

  protected @Nullable ImageIcon getImageIcon(EvaluationContextImpl evaluationContext, Value obj, String methodName) {
    byte[] data = getImageBytesFromHelper(evaluationContext, obj, methodName);
    return data != null ? new ImageIcon(data) : null;
  }
}
