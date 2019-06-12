// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.ui.scale.JBUIScale;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class BackButton extends JButton {
  private Runnable myRunnable;

  public BackButton() {
    super("Plugins");

    setIcon(new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        AllIcons.Actions.Back.paintIcon(c, g, x + JBUIScale.scale(7), y);
      }

      @Override
      public int getIconWidth() {
        return AllIcons.Actions.Back.getIconWidth() + JBUIScale.scale(7);
      }

      @Override
      public int getIconHeight() {
        return AllIcons.Actions.Back.getIconHeight();
      }
    });

    setHorizontalAlignment(SwingConstants.LEFT);

    Dimension size = getPreferredSize();
    size.width -= JBUIScale.scale(15);
    size.height = JBUIScale.scale(27);
    setPreferredSize(size);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    myRunnable = EventHandler.addGlobalAction(this, IdeActions.ACTION_GOTO_BACK, () -> doClick());
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (myRunnable != null) {
      myRunnable.run();
      myRunnable = null;
    }
  }
}