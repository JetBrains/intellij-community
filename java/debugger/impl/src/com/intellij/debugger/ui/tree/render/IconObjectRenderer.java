/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* Created by Egor on 04.10.2014.
*/
class IconObjectRenderer extends ToStringBasedRenderer implements FullValueEvaluatorProvider {
  public IconObjectRenderer(final NodeRendererSettings rendererSettings) {
    super(rendererSettings, "Icon", null, null);
    setClassName("javax.swing.Icon");
    setEnabled(true);
  }

  @Override
  public Icon calcValueIcon(final ValueDescriptor descriptor, final EvaluationContext evaluationContext, final DescriptorLabelListener listener)
    throws EvaluateException {
    EvaluationContextImpl evalContext = ((EvaluationContextImpl)evaluationContext);
    DebugProcessImpl debugProcess = evalContext.getDebugProcess();

    if (!Registry.is("debugger.auto.fetch.icons") || DebuggerUtilsImpl.isRemote(debugProcess)) return null;

    debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(evalContext.getSuspendContext()) {
      @Override
      public void contextAction() throws Exception {
        String getterName = AllIcons.Debugger.Value.getIconHeight() <= 16 ? "iconToBytesPreviewNormal" : "iconToBytesPreviewRetina";
        descriptor.setValueIcon(ImageObjectRenderer.getIcon(evaluationContext, descriptor.getValue(), getterName));
        listener.labelChanged();
      }
    });
    return null;
  }

  @Nullable
  @Override
  public XFullValueEvaluator getFullValueEvaluator(final EvaluationContextImpl evaluationContext, final ValueDescriptorImpl valueDescriptor) {
    return new ImageObjectRenderer.IconPopupEvaluator(DebuggerBundle.message("message.node.show.icon"), evaluationContext) {
      @Override
      protected Icon getData() {
        return ImageObjectRenderer.getIcon(getEvaluationContext(), valueDescriptor.getValue(), "iconToBytes");
      }
    };
  }
}
