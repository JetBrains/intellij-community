// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author gregsh
 */
public class ViewInplaceCommentsAction extends DumbAwareToggleAction {
  {
    setEnabledInModalContext(true);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return UISettings.getInstance().getShowInplaceComments();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    UISettings.getInstance().setShowInplaceComments(state);
    updateAllTreesCellsWidth();
    IdeBackgroundUtil.repaintAllWindows();
  }

  private static void updateAllTreesCellsWidth() {
    for (JTree tree : UIUtil.uiTraverser(null).withRoots(Window.getWindows()).filter(JTree.class)) {
      //noinspection deprecation
      TreeUtil.invalidateCacheAndRepaint(tree.getUI());
    }
  }
}
