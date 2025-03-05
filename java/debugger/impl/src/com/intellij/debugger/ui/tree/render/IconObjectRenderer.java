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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class IconObjectRenderer extends CompoundRendererProvider {
  @Override
  protected String getName() {
    return "Icon";
  }

  @Override
  protected String getClassName() {
    return "javax.swing.Icon";
  }

  @Override
  protected boolean isEnabled() {
    return true;
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
          String getterName = AllIcons.Debugger.Value.getIconHeight() <= 16 ? "iconToBytesPreviewNormal" : "iconToBytesPreviewRetina";
          descriptor.setValueIcon(ImageObjectRenderer.getIcon(evalContext, descriptor.getValue(), getterName));
          listener.labelChanged();
        }
      });
      return null;
    };
  }

  @Override
  protected FullValueEvaluatorProvider getFullValueEvaluatorProvider() {
    return (evaluationContext, valueDescriptor) -> {
      return new ImageObjectRenderer.IconPopupEvaluator(JavaDebuggerBundle.message("message.node.show.icon"), evaluationContext) {
        @Override
        protected Icon getData() {
          return ImageObjectRenderer.getIcon(getEvaluationContext(), valueDescriptor.getValue(), "iconToBytes");
        }
      };
    };
  }
}
