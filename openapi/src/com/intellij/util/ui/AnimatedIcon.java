package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class AnimatedIcon extends JComponent implements Disposable {

  private Icon[] myIcons;
  private Dimension myPrefSize = new Dimension();

  private int myCurrentIconIndex;

  private Icon myPassiveIcon;

  private boolean myRunning = true;

  protected Animator myAnimator;

  private String myName;

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
      public void paintNow(final int frame) {
        myCurrentIconIndex = frame;
        paintImmediately(0, 0, getWidth(), getHeight());
      }

      protected void onAnimationMaxCycleReached() throws InterruptedException {
        AnimatedIcon.this.onAnimationMaxCycleReached();
      }

      public boolean isAnimated() {
        return AnimatedIcon.this.isAnimated();
      }
    };
  }

  protected void onAnimationMaxCycleReached() throws InterruptedException {

  }

  public void resume() {
    myRunning = true;
    myAnimator.resume();
  }

  public void addNotify() {
    super.addNotify();
    if (myRunning) {
      myAnimator.resume();
    }
  }

  public void removeNotify() {
    super.removeNotify();
    myAnimator.suspend();
  }

  public void suspend() {
    myRunning = false;
    myAnimator.suspend();
    repaint();
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
    g.setColor(UIUtil.getBgFillColor(this));
    g.fillRect(0, 0, getWidth(), getHeight());

    Icon icon;

    if (myAnimator.isRunning()) {
      icon = myIcons[myCurrentIconIndex];
    } else {
      icon = getPassiveIcon();
    }

    final Dimension size = getSize();
    int x = (size.width - icon.getIconWidth()) / 2;
    int y = (size.height - icon.getIconHeight()) / 2;

    icon.paintIcon(this, g, x, y);
  }

  protected Icon getPassiveIcon() {
    return myPassiveIcon;
  }


  public boolean isAnimated() {
    return true;
  }
}
