/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.popup.list.ListPopupImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class NavBarListener implements ActionListener, FocusListener {
  private final NavBarPanel myPanel;

  NavBarListener(NavBarPanel panel) {
    myPanel = panel;
    for (NavBarKeyboardCommand command : NavBarKeyboardCommand.values()) {
      registerKey(command);
    }
    myPanel.addFocusListener(this);
  }

  private void registerKey(NavBarKeyboardCommand cmd) {
    myPanel.registerKeyboardAction(this, cmd.name(), cmd.getKeyStroke(), JComponent.WHEN_FOCUSED);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    final NavBarKeyboardCommand cmd = NavBarKeyboardCommand.fromString(e.getActionCommand());
    if (cmd != null) {
      switch (cmd) {
        case LEFT:     myPanel.moveLeft();  break;
        case RIGHT:    myPanel.moveRight(); break;
        case HOME:     myPanel.moveHome();  break;
        case END:      myPanel.moveEnd();   break;
        case DOWN:     myPanel.moveDown();  break;
        case ENTER:    myPanel.enter();     break;
        case ESCAPE:   myPanel.escape();    break;
        case NAVIGATE: myPanel.navigate();  break;
      }
    }
  }

  public void focusGained(final FocusEvent e) {
    myPanel.updateItems();
    final ArrayList<NavBarItem> items = myPanel.getItems();
    if (!myPanel.isInFloatingMode() && items.size() > 0) {
      myPanel.setContextComponent(items.get(items.size() - 1));
    } else {
      myPanel.setContextComponent(null);
    }
  }

  public void focusLost(final FocusEvent e) {
    if (myPanel.getProject().isDisposed()) {
      myPanel.setContextComponent(null);
      myPanel.hideHint();
      return;
    }

    // required invokeLater since in current call sequence KeyboardFocusManager is not initialized yet
    // but future focused component
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        processFocusLost(e);
      }
    });
  }

  private void processFocusLost(FocusEvent e) {
    final ListPopupImpl nodePopup = myPanel.getNodePopup();
    final boolean nodePopupInactive = nodePopup == null || !nodePopup.isVisible() || !nodePopup.isFocused();
    boolean childPopupInactive = !JBPopupFactory.getInstance().isChildPopupFocused(myPanel);
    if (nodePopupInactive && childPopupInactive) {
      final Component opposite = e.getOppositeComponent();
      if (opposite != null && opposite != myPanel && !myPanel.isAncestorOf(opposite) && !e.isTemporary()) {
        myPanel.setContextComponent(null);
        myPanel.hideHint();
      }
    }

    myPanel.updateItems();
  }
}
