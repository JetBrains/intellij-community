package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class ScrollTreeToCenterAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    if (component instanceof JTree) {
      JTree tree = (JTree)component;
      final int[] selection = tree.getSelectionRows();
      if (selection.length > 0) {
        TreeUtil.showRowCentered(tree, selection [0], false);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformDataKeys.CONTEXT_COMPONENT) instanceof JTree);
  }
}
