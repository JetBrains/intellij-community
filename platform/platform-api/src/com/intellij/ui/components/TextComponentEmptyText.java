// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class TextComponentEmptyText extends StatusText {
  public static final String STATUS_VISIBLE_FUNCTION = "StatusVisibleFunction";

  private final JTextComponent myOwner;
  private String myStatusTriggerText = "";

  TextComponentEmptyText(JTextComponent owner) {
    super(owner);
    myOwner = owner;
    clear();
    myOwner.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myOwner.repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        myOwner.repaint();
      }
    });
  }

  public void setTextToTriggerStatus(@NotNull String defaultText) {
    myStatusTriggerText = defaultText;
  }

  @NotNull
  public String getStatusTriggerText() {
    return myStatusTriggerText;
  }

  public void paintStatusText(Graphics g) {
    if (!isFontSet()) {
      setFont(myOwner.getFont());
    }
    paint(myOwner, g);
  }

  @Override
  protected boolean isStatusVisible() {
    return myOwner.getText().equals(myStatusTriggerText) && !myOwner.isFocusOwner();
  }

  @Override
  protected Rectangle getTextComponentBound() {
    Rectangle b = myOwner.getBounds();
    Insets insets = ObjectUtils.notNull(myOwner.getInsets(), JBInsets.emptyInsets());
    Insets margin = ObjectUtils.notNull(myOwner.getMargin(), JBInsets.emptyInsets());
    Insets ipad = getComponent().getIpad();
    int left = insets.left + margin.left - ipad.left;
    int right = insets.right + margin.right - ipad.right;
    int top = insets.top + margin.top - ipad.top;
    int bottom = insets.bottom + margin.bottom - ipad.bottom;
    return new Rectangle(left, top,
                         b.width - left - right,
                         b.height - top - bottom);
  }

  @NotNull
  @Override
  protected Rectangle adjustComponentBounds(@NotNull JComponent component, @NotNull Rectangle bounds) {
    Dimension size = component.getPreferredSize();
    return component == getComponent()
           ? new Rectangle(bounds.x, bounds.y, size.width, bounds.height)
           : new Rectangle(bounds.x + bounds.width - size.width, bounds.y, size.width, bounds.height);
  }
}
