// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.ui.tree.render.CustomFieldInplaceEditor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * @author egor
 */
public class NewCustomFieldAction extends XDebuggerTreeActionBase {
  private static final Icon ClassLevelWatch = IconLoader.getIcon("/debugger/classLevelWatch.svg");

  public NewCustomFieldAction() {
    getTemplatePresentation().setIcon(ClassLevelWatch);
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    CustomFieldInplaceEditor.editNew(node);
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(ViewAsGroup.getSelectedValues(e).size() == 1);
  }
}
