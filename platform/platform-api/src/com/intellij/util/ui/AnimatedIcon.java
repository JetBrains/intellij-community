// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public class AnimatedIcon extends JComponent implements Disposable {
  private final Icon[] icons;
  private Dimension preferredSize;

  private int currentIconIndex;

  protected final Icon passiveIcon;
  private final Icon emptyPassiveIcon;

  private boolean isPaintPassive = true;
  private boolean isRunning = true;

  protected final Animator animator;

  private final String name;

  public AnimatedIcon(@NonNls String name, Icon[] icons, Icon passiveIcon, int cycleLength) {
    this(name, icons, passiveIcon, cycleLength, null);
  }

  public AnimatedIcon(@NonNls String name, Icon[] icons, Icon passiveIcon, int cycleLength, @Nullable CoroutineScope coroutineScope) {
    this.name = name;
    this.icons = icons.length == 0 ? new Icon[]{passiveIcon} : icons;
    this.passiveIcon = passiveIcon;
    preferredSize = calcPreferredSize();

    animator = new Animator(name, icons.length, cycleLength, true, true, coroutineScope) {
      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        int len = AnimatedIcon.this.icons.length;
        currentIconIndex = frame < 0 ? 0 : frame >= len ? len - 1 : frame;
        paintImmediately(0, 0, getWidth(), getHeight());
      }
    };

    emptyPassiveIcon = icons.length > 0 ? EmptyIcon.create(icons[0]) : EmptyIcon.ICON_0;

    setOpaque(false);

    UiNotifyConnector.installOn(this, new Activatable() {
      @Override
      public void showNotify() {
        if (isRunning) {
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

    for (Icon each : icons) {
      dimension.width = Math.max(each.getIconWidth(), dimension.width);
      dimension.height = Math.max(each.getIconHeight(), dimension.height);
    }

    return new Dimension(Math.max(passiveIcon.getIconWidth(), dimension.width), Math.max(passiveIcon.getIconHeight(), dimension.height));
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (getParent() != null) preferredSize = calcPreferredSize();
  }

  public void setPaintPassiveIcon(boolean paintPassive) {
    isPaintPassive = paintPassive;
  }

  private boolean ensureAnimation(boolean running) {
    boolean changes = animator.isRunning() != running;

    if (running) {
      animator.resume();
    }
    else {
      animator.suspend();
    }

    return changes;
  }

  public void resume() {
    isRunning = true;
    ensureAnimation(true);
  }

  public void suspend() {
    isRunning = false;
    if (ensureAnimation(false)) {
      repaint();
    }
  }

  @Override
  public void dispose() {
    Disposer.dispose(animator);
  }

  @Override
  public Dimension getPreferredSize() {
    Insets insets = getInsets();
    return new Dimension(preferredSize.width + insets.left + insets.right, preferredSize.height + insets.top + insets.bottom);
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
      Container parent = getParent();
      JComponent opaque = null;
      if (parent instanceof JComponent) {
        opaque = (JComponent)UIUtil.findNearestOpaque(parent);
      }

      Color bg = opaque == null ? UIUtil.getPanelBackground() : opaque.getBackground();
      g.setColor(bg);
      g.fillRect(0, 0, getWidth(), getHeight());
    }

    Icon icon;
    if (animator.isRunning()) {
      icon = icons[currentIconIndex];
    }
    else {
      icon = getPassiveIcon();
    }

    Rectangle bounds = new Rectangle(getWidth(), getHeight());
    JBInsets.removeFrom(bounds, getInsets());
    bounds.x += (bounds.width - icon.getIconWidth()) / 2;
    bounds.y += (bounds.height - icon.getIconHeight()) / 2;
    paintIcon(g, icon, bounds.x, bounds.y);
  }

  protected void paintIcon(Graphics g, Icon icon, int x, int y) {
    icon.paintIcon(this, g, x, y);
  }

  protected Icon getPassiveIcon() {
    return isPaintPassive ? passiveIcon : emptyPassiveIcon;
  }

  public boolean isRunning() {
    return animator.isRunning();
  }

  @Override
  public String toString() {
    return name + " isRunning=" + isRunning + " isOpaque=" + isOpaque() + " paintPassive=" + isPaintPassive;
  }
}
