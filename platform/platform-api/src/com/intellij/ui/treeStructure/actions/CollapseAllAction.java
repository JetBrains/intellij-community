/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.ui.treeStructure.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@SuppressWarnings("ComponentNotRegistered")
public class CollapseAllAction extends AnAction implements DumbAware {

  protected JTree myTree;

  public CollapseAllAction(JTree tree) {
    super("Collapse All", "", AllIcons.Actions.Collapseall);
    myTree = tree;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    int row = getTree().getRowCount() - 1;
    while (row >= 0) {
      getTree().collapseRow(row);
      row--;
    }
  }

  protected JTree getTree() {
    return myTree;
  }
}
