/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;

import javax.swing.*;
import java.awt.*;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public class AnimatedIcon extends JComponent implements Disposable {
  private final Icon[] myIcons;
  private final Dimension myPrefSize;

  private int myCurrentIconIndex;

  protected final Icon myPassiveIcon;
  private final Icon myEmptyPassiveIcon;

  private boolean myPaintPassive = true;
  private boolean myRunning = true;

  protected final Animator myAnimator;

  private final String myName;

  public AnimatedIcon(final String name, Icon[] icons, Icon passiveIcon, int cycleLength) {
    myName = name;
    myIcons = icons.length == 0 ? new Icon[]{passiveIcon} : icons;
    myPassiveIcon = passiveIcon;
    myPrefSize = calcPreferredSize();

    myAnimator = new Animator(myName, icons.length, cycleLength, true) {
      @Override
      public void paintNow(final int frame, final int totalFrames, final int cycle) {
        final int len = myIcons.length;
        myCurrentIconIndex = frame < 0 ? 0 : frame >= len ? len - 1 : frame;
        paintImmediately(0, 0, getWidth(), getHeight());
      }
    };

    if (icons.length > 0) {
      myEmptyPassiveIcon = EmptyIcon.create(icons[0]);
    }
    else {
      myEmptyPassiveIcon = EmptyIcon.ICON_0;
    }

    setOpaque(false);

    new UiNotifyConnector(this, new Activatable() {
      @Override
      public void showNotify() {
        if (myRunning) {
          ensureAnimation(true);
        }
      }

      @Override
      public void hideNotify() {
        ensureAnimation(false);
      }
    });
  }

  protected Dimension calcPreferredSize() {
    Dimension dimension = new Dimension();

    for (Icon each : myIcons) {
      dimension.width = Math.max(each.getIconWidth(), dimension.width);
      dimension.height = Math.max(each.getIconHeight(), dimension.height);
    }

    return new Dimension(
      Math.max(myPassiveIcon.getIconWidth(), dimension.width),
      Math.max(myPassiveIcon.getIconHeight(), dimension.height));
  }

  public void setPaintPassiveIcon(boolean paintPassive) {
    myPaintPassive = paintPassive;
  }

  private boolean ensureAnimation(boolean running) {
    boolean changes = myAnimator.isRunning() != running;

    if (running) {
      myAnimator.resume();
    }
    else {
      myAnimator.suspend();
    }

    return changes;
  }

  public void resume() {
    myRunning = true;
    ensureAnimation(true);
  }

  public void suspend() {
    myRunning = false;
    if (ensureAnimation(false)) {
      repaint();
    }
  }

  @Override
  public void dispose() {
    Disposer.dispose(myAnimator);
  }

  @Override
  public Dimension getPreferredSize() {
    final Insets insets = getInsets();
    return new Dimension(myPrefSize.width + insets.left + insets.right, myPrefSize.height + insets.top + insets.bottom);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  protected void paintComponent(Graphics g) {
    //if (myPaintingBgNow) return;

    if (isOpaque()) {
      final Container parent = getParent();
      JComponent opaque = null;
      if (parent instanceof JComponent) {
        opaque = (JComponent)UIUtil.findNearestOpaque(parent);
      }
      Color bg = opaque != null ? opaque.getBackground() : UIUtil.getPanelBackground();
      g.setColor(bg);
      g.fillRect(0, 0, getWidth(), getHeight());
    }

    Icon icon;

    if (myAnimator.isRunning()) {
      icon = myIcons[myCurrentIconIndex];
    }
    else {
      icon = getPassiveIcon();
    }

    final Dimension size = getSize();
    int x = (size.width - icon.getIconWidth()) / 2;
    int y = (size.height - icon.getIconHeight()) / 2;

    paintIcon(g, icon, x, y);
  }

  protected void paintIcon(Graphics g, Icon icon, int x, int y) {
    icon.paintIcon(this, g, x, y);
  }

  protected Icon getPassiveIcon() {
    return myPaintPassive ? myPassiveIcon : myEmptyPassiveIcon;
  }

  public boolean isRunning() {
    return myAnimator.isRunning();
  }

  @Override
  public String toString() {
    return myName + " isRunning=" + myRunning + " isOpaque=" + isOpaque() + " paintPassive=" + myPaintPassive;
  }
}
