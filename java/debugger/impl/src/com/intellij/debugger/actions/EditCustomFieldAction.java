// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public void update(@NotNull final AnActionEvent e) {
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
