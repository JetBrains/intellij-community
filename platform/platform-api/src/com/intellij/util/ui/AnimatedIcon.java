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

package com.intellij.util.ui;

import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.*;

public abstract class AnimatedIcon extends JComponent implements Disposable {

  private Icon[] myIcons;
  private Dimension myPrefSize = new Dimension();

  private int myCurrentIconIndex;

  private Icon myPassiveIcon;

  private Icon myEmptyPassiveIcon;
  private boolean myPaintPassive = true;

  private boolean myRunning = true;

  protected Animator myAnimator;

  private final String myName;

  private boolean myLastPaintWasRunning;
  private boolean myPaintingBgNow;

  protected AnimatedIcon(final String name) {
    myName = name;
  }

  protected final void init(Icon[] icons, Icon passiveIcon, int cycleLength, final int interCycleGap, final int maxRepeatCount) {
    myIcons = icons;
    myPassiveIcon = passiveIcon;

    myPrefSize = new Dimension();
    for (Icon each : icons) {
      myPrefSize.width = Math.max(each.getIconWidth(), myPrefSize.width);
      myPrefSize.height = Math.max(each.getIconHeight(), myPrefSize.height);
    }

    myPrefSize.width = Math.max(passiveIcon.getIconWidth(), myPrefSize.width);
    myPrefSize.height = Math.max(passiveIcon.getIconHeight(), myPrefSize.height);

    UIUtil.removeQuaquaVisualMarginsIn(this);

    myAnimator = new Animator(myName, icons.length, cycleLength, true, interCycleGap, maxRepeatCount) {
      public void paintNow(final float frame, final float totalFrames, final float cycle) {
        myCurrentIconIndex = (int)frame;
        paintImmediately(0, 0, getWidth(), getHeight());
      }

      protected void onAnimationMaxCycleReached() throws InterruptedException {
        AnimatedIcon.this.onAnimationMaxCycleReached();
      }

      public boolean isAnimated() {
        return AnimatedIcon.this.isAnimated();
      }
    };


    if (icons.length > 0) {
      myEmptyPassiveIcon = new EmptyIcon(icons[0]);
    } else {
      myEmptyPassiveIcon = new EmptyIcon(0);
    }

    setOpaque(false);
  }

  public void setPaintPassiveIcon(boolean paintPassive) {
    myPaintPassive = paintPassive;
  }

  protected void onAnimationMaxCycleReached() throws InterruptedException {

  }

  private boolean ensureAnimation(boolean running) {
    boolean changes = myAnimator.isRunning() != running;

    if (running) {
      myAnimator.resume();
    } else {
      myAnimator.suspend();
    }

    return changes;
  }

  public void addNotify() {
    super.addNotify();
    if (myRunning) {
      ensureAnimation(true);
    }
  }

  public void removeNotify() {
    super.removeNotify();
    ensureAnimation(false);
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

  public void dispose() {
    myAnimator.dispose();
  }

  public Dimension getPreferredSize() {
    final Insets insets = getInsets();
    return new Dimension(myPrefSize.width + insets.left + insets.right, myPrefSize.height + insets.top + insets.bottom);
  }

  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  protected void paintComponent(Graphics g) {
    if (myPaintingBgNow) return;

    if (isOpaque()) {
      final Container parent = getParent();
      JComponent opaque = null;
      if (parent instanceof JComponent) {
        opaque = (JComponent)UIUtil.findNearestOpaque((JComponent)parent);
      }
      Color bg = opaque != null ? opaque.getBackground() : UIManager.getColor("Panel.background");
      g.setColor(bg);
      g.fillRect(0, 0, getWidth(), getHeight());
    }

    Icon icon;

    if (myAnimator.isRunning()) {
      icon = myIcons[myCurrentIconIndex];
    } else {
      icon = getPassiveIcon();
    }

    final Dimension size = getSize();
    int x = (size.width - icon.getIconWidth()) / 2;
    int y = (size.height - icon.getIconHeight()) / 2;

    paintIcon(g, icon, x, y);

    myLastPaintWasRunning = myAnimator.isRunning();
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

  public boolean isAnimated() {
    return true;
  }

  @Override
  public String toString() {
    return myName + " isRunning=" + myRunning + " isOpaque=" + isOpaque() + " paintPassive=" + myPaintPassive;
  }
}
