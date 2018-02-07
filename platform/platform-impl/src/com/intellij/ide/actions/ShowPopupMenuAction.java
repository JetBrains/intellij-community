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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PopupAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Vladimir Kondratyev
 */
public class ShowPopupMenuAction extends DumbAwareAction implements PopupAction {
  public ShowPopupMenuAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
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
      0, point2.x, coord, 1, true);
    for (Component cur = deepest; cur != null; cur = cur.getParent()) {
      cur.dispatchEvent(event);
      if (event.isConsumed()) break;
    }
  }

  public void update(AnActionEvent e) {
    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    e.getPresentation().setEnabled(focusManager.getFocusOwner() instanceof JComponent);
  }
}