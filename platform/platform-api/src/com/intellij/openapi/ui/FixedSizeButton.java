// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * This class represents non-resizable, non-focusable button with the
 * same height and length.
 */
public class FixedSizeButton extends JButton {
  private int mySize;
  private JComponent myComponent;

  public FixedSizeButton() {
    this(-1, null);
  }

  private FixedSizeButton(int size, JComponent component) {
    Icon icon = AllIcons.General.Ellipsis;
    if (icon != null) {
      // loading may fail at design time
      setIcon(icon);
    }
    else {
      setText(".");
    }
    mySize = size;
    myComponent = component;
    setMargin(JBInsets.emptyInsets());
    setDefaultCapable(false);
    setFocusable(false);
    if (UIUtil.isUnderIntelliJLaF() || StartupUiUtil.isUnderDarcula()) {
      putClientProperty("JButton.buttonType", "square");
    }
  }

  /**
   * Creates the {@code FixedSizeButton} with specified size.
   *
   * @throws IllegalArgumentException
   *          if {@code size} isn't
   *          positive integer number.
   */
  public FixedSizeButton(int size) {
    this(size, null);
    if (size <= 0) {
      throw new IllegalArgumentException("wrong size: " + size);
    }
  }

  /**
   * Creates the {@code FixedSizeButton} which size is equals to
   * {@code component.getPreferredSize().height}. It is very convenient
   * way to create "browse" like button near the text fields.
   */
  public FixedSizeButton(@NotNull JComponent component) {
    this(-1, component);
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
  public Dimension getPreferredSize() {
    if (mySize != -1) {
      return new Dimension(mySize, mySize);
    }

    Dimension d = super.getPreferredSize();
    int base = new JTextField().getPreferredSize().height;
    if (base %2 == 1) base++;
    d.width = Math.max(d.height, base);
    int width = mySize == -1 ? d.width : mySize;
    int height = myComponent != null ? myComponent.getPreferredSize().height : mySize != -1 ? mySize : base;

    return new Dimension(width, height);
  }

  public void setAttachedComponent(JComponent component) {
    myComponent = component;
  }

  public JComponent getAttachedComponent() {
    return myComponent;
  }

  public void setSize(int size) {
    mySize = size;
  }
}
