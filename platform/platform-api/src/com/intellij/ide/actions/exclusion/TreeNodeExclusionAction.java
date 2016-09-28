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
package com.intellij.ide.actions.exclusion;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;

/**
 * @author Dmitry Batkovich.
 */
abstract class TreeNodeExclusionAction<T extends MutableTreeNode> extends AnAction {
  private final static Logger LOG = Logger.getInstance(TreeNodeExclusionAction.class);

  private final boolean myIsExclude;

  TreeNodeExclusionAction(boolean isExclude) {
    myIsExclude = isExclude;
    getTemplatePresentation().setText(getActionText());
  }

  @Override
  public void update(AnActionEvent e) {
    final ExclusionHandler<T> exclusionProcessor = ExclusionHandler.EXCLUSION_HANDLER.getData(e.getDataContext());
    if (exclusionProcessor == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    final Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
    final Presentation presentation = e.getPresentation();
    if (!(component instanceof JTree) || !exclusionProcessor.isActionEnabled(myIsExclude)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    JTree tree = (JTree) component;
    final TreePath[] selection = TreeUtil.selectMaximals(tree.getSelectionPaths());
    if (selection.length == 0) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    final boolean[] isEnabled = {false};
    for (TreePath path : selection) {
      final T node = (T)path.getLastPathComponent();
      TreeUtil.traverse(node, n -> {
        if (!exclusionProcessor.isNodeExclusionAvailable((T)n)) return true;
        final Boolean isNodeExcluded = exclusionProcessor.isNodeExcluded((T)n);
        if (myIsExclude != isNodeExcluded) {
          isEnabled[0] = true;
          return false;
        }
        return true;
      });
    }
    presentation.setEnabledAndVisible(isEnabled[0]);
    if (isEnabled[0]) {
      String text = getActionText();
      if (selection.length > 1) {
        text += " All";
      }
      presentation.setText(text);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final JTree tree = (JTree)PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
    LOG.assertTrue(tree != null);
    final TreePath[] paths = tree.getSelectionPaths();
    LOG.assertTrue(paths != null);
    final ExclusionHandler<T> exclusionProcessor = ExclusionHandler.EXCLUSION_HANDLER.getData(e.getDataContext());
    LOG.assertTrue(exclusionProcessor != null);
    for (TreePath path : paths) {
      final T node = (T)path.getLastPathComponent();
      TreeUtil.traverse(node, n -> {
        if (!exclusionProcessor.isNodeExclusionAvailable((T)n)) return true;
        if (Boolean.valueOf(myIsExclude) != exclusionProcessor.isNodeExcluded((T)n)) {
          if (myIsExclude) {
            exclusionProcessor.excludeNode(node);
          } else {
            exclusionProcessor.includeNode(node);
          }
        }
        return true;
      });
    }
    exclusionProcessor.onDone(myIsExclude);
  }

  private String getActionText() {
    return myIsExclude ? "Exclude" : "Include";
  }
}
