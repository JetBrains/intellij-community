/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Vladislav.Soroka
 * @since 10/20/2014
 */
public abstract class ExternalSystemTreeAction extends ExternalSystemAction {
  @Override
  protected boolean isEnabled(AnActionEvent e) {
    return super.isEnabled(e) && getTree(e) != null;
  }

  @Nullable
  protected static JTree getTree(AnActionEvent e) {
    return ExternalSystemDataKeys.PROJECTS_TREE.getData(e.getDataContext());
  }

  public static class CollapseAll extends ExternalSystemTreeAction {
    public void actionPerformed(AnActionEvent e) {
      JTree tree = getTree(e);
      if (tree == null) return;

      int row = tree.getRowCount() - 1;
      while (row >= 0) {
        tree.collapseRow(row);
        row--;
      }
    }
  }

  public static class ExpandAll extends ExternalSystemTreeAction {
    public void actionPerformed(AnActionEvent e) {
      JTree tree = getTree(e);
      if (tree == null) return;

      for (int i = 0; i < tree.getRowCount(); i++) {
        tree.expandRow(i);
      }
    }
  }
}

