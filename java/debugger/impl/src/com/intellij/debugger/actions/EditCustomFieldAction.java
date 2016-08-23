/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.ui.impl.watch.UserExpressionDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.CustomFieldInplaceEditor;
import com.intellij.debugger.ui.tree.render.EnumerationChildrenRenderer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author egor
 */
public class EditCustomFieldAction extends XDebuggerTreeActionBase {
  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    ValueDescriptorImpl descriptor = ((JavaValue)node.getValueContainer()).getDescriptor();
    EnumerationChildrenRenderer enumerationChildrenRenderer = getParentEnumerationRenderer(descriptor);
    if (enumerationChildrenRenderer != null) {
      new CustomFieldInplaceEditor(node, (UserExpressionDescriptorImpl)descriptor, enumerationChildrenRenderer).show();
    }
  }

  public void update(final AnActionEvent e) {
    boolean enabled = false;
    List<JavaValue> values = ViewAsGroup.getSelectedValues(e);
    if (values.size() == 1) {
      enabled = getParentEnumerationRenderer(values.get(0).getDescriptor()) != null;
    }
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Nullable
  static EnumerationChildrenRenderer getParentEnumerationRenderer(ValueDescriptorImpl descriptor) {
    if (descriptor instanceof UserExpressionDescriptorImpl) {
      return EnumerationChildrenRenderer.getCurrent(((UserExpressionDescriptorImpl)descriptor).getParentDescriptor());
    }
    return null;
  }
}
