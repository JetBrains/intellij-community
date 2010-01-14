/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions.tree;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.ui.treeStructure.treetable.TreeTable;

import javax.swing.*;

abstract class BaseTreeNodeAction extends AnAction {
  public BaseTreeNodeAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    Object sourceComponent = getSourceComponent(e);
    if (sourceComponent instanceof JTree) {
      performOn((JTree)sourceComponent);
    }
    else if (sourceComponent instanceof TreeTable) {
      performOn(((TreeTable)sourceComponent).getTree());
    }
  }

  protected abstract void performOn(JTree tree);

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(enabledOn(getSourceComponent(e)));
  }

  private static boolean enabledOn(Object sourceComponent) {
    if (sourceComponent instanceof JTree) {
      return true;
    }
    if (sourceComponent instanceof TreeTable) {
      return true;
    }
    return false;
  }

  private static Object getSourceComponent(AnActionEvent e) {
    return PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
  }
}
