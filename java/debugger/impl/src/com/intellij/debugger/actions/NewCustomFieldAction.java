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

import com.intellij.debugger.ui.tree.render.CustomFieldInplaceEditor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author egor
 */
public class NewCustomFieldAction extends XDebuggerTreeActionBase {
  public NewCustomFieldAction() {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(AllIcons.Debugger.Watch, 1);
    icon.setIcon(IconUtil.scale(AllIcons.Nodes.Class, 0.7), 0, SwingConstants.NORTH_EAST);
    getTemplatePresentation().setIcon(icon);
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    CustomFieldInplaceEditor.editNew(node);
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(ViewAsGroup.getSelectedValues(e).size() == 1);
  }
}
