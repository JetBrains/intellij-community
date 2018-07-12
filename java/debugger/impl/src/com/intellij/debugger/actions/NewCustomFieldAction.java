// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.CustomFieldInplaceEditor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author egor
 */
public class NewCustomFieldAction extends XDebuggerTreeActionBase {
  public NewCustomFieldAction() {
    getTemplatePresentation().setIcon(AllIcons.Debugger.ClassLevelWatch);
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    CustomFieldInplaceEditor.editNew(node);
  }

  public void update(final AnActionEvent e) {
    boolean enabled = false;
    List<JavaValue> values = ViewAsGroup.getSelectedValues(e);
    if (values.size() == 1) {
      ValueDescriptorImpl descriptor = values.get(0).getDescriptor();
      enabled = descriptor.isValueReady() && descriptor.getType() != null;
    }
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
