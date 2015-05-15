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

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;

/**
 * @author egor
 */
public abstract class ToStringBasedRenderer extends CompoundReferenceRenderer {
  public ToStringBasedRenderer(NodeRendererSettings rendererSettings,
                               String name,
                               ValueLabelRenderer labelRenderer,
                               ChildrenRenderer childrenRenderer) {
    super(rendererSettings, name, labelRenderer, childrenRenderer);
  }

  public String calcLabel(ValueDescriptor descriptor,
                          EvaluationContext evaluationContext,
                          DescriptorLabelListener listener) throws EvaluateException {
    String res = calcToStringLabel(descriptor, evaluationContext, listener);
    if (res != null) {
      return res;
    }
    return super.calcLabel(descriptor, evaluationContext, listener);
  }

  protected String calcToStringLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    final ToStringRenderer toStringRenderer = myRendererSettings.getToStringRenderer();
    if (toStringRenderer.isEnabled() && DebuggerManagerEx.getInstanceEx(evaluationContext.getProject()).getContext().isEvaluationPossible()) {
      return toStringRenderer.calcLabel(descriptor, evaluationContext, listener);
    }
    return null;
  }

}
