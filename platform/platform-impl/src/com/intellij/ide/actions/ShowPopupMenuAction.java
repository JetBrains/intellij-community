// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PopupAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class ShowPopupMenuAction extends DumbAwareAction implements PopupAction {
  public ShowPopupMenuAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    RelativePoint relPoint = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());

    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component focusOwner = focusManager.getFocusOwner();

    Point point1 = relPoint.getPoint(focusOwner);
    Component deepest = ObjectUtils.notNull(UIUtil.getDeepestComponentAt(focusOwner, point1.x, point1.y / 2), focusOwner);
    Point point2 = relPoint.getPoint(deepest);

    Editor editor = e.getData(CommonDataKeys.EDITOR);
    int coord = editor != null
                ? Math.max(0, point2.y - 1) //To avoid cursor jump to the line below. http://www.jetbrains.net/jira/browse/IDEADEV-10644
                : point2.y;

    MouseEvent event = new MouseEvent(
      focusOwner, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
      0, point2.x, coord, 1, true, MouseEvent.BUTTON3);
    for (Component cur = deepest; cur != null; cur = cur.getParent()) {
      cur.dispatchEvent(event);
      if (event.isConsumed()) break;
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    e.getPresentation().setEnabled(focusManager.getFocusOwner() instanceof JComponent);
  }
}