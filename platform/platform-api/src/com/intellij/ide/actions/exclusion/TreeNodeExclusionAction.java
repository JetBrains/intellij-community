// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.exclusion;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.tree.TreeCollector.TreePathRoots;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

abstract class TreeNodeExclusionAction<T extends TreeNode> extends AnAction {
  private final static Logger LOG = Logger.getInstance(TreeNodeExclusionAction.class);

  private final boolean myIsExclude;

  TreeNodeExclusionAction(boolean isExclude) {
    myIsExclude = isExclude;
    getTemplatePresentation().setText(getActionText());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final ExclusionHandler<T> exclusionProcessor = e.getData(ExclusionHandler.EXCLUSION_HANDLER);
    if (exclusionProcessor == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    final Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    final Presentation presentation = e.getPresentation();
    if (!(component instanceof JTree) || !exclusionProcessor.isActionEnabled(myIsExclude)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    JTree tree = (JTree) component;
    List<TreePath> selection = TreePathRoots.collect(tree.getSelectionPaths());
    if (selection.isEmpty()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    final boolean[] isEnabled = {false};
    for (TreePath path : selection) {
      final T node = (T)path.getLastPathComponent();
      TreeUtil.traverse(node, n -> {
        if (!exclusionProcessor.isNodeExclusionAvailable((T)n)) return true;
        boolean isNodeExcluded = exclusionProcessor.isNodeExcluded((T)n);
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
      if (selection.size() > 1) {
        text += " All";
      }
      presentation.setText(text);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final JTree tree = (JTree)e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    LOG.assertTrue(tree != null);
    final TreePath[] paths = tree.getSelectionPaths();
    LOG.assertTrue(paths != null);
    final ExclusionHandler<T> exclusionProcessor = e.getData(ExclusionHandler.EXCLUSION_HANDLER);
    LOG.assertTrue(exclusionProcessor != null);
    for (TreePath path : paths) {
      final T node = (T)path.getLastPathComponent();
      TreeUtil.traverse(node, n -> {
        if (!exclusionProcessor.isNodeExclusionAvailable((T)n)) return true;
        if (myIsExclude != exclusionProcessor.isNodeExcluded((T)n)) {
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
