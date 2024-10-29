// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.AnchorableComponent;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import java.awt.*;

/**
 * @author evgeny.zakrevsky
 */
public class JBCheckBox extends JCheckBox implements AnchorableComponent {
  private JComponent myAnchor;

  public JBCheckBox() {
    this(null);
  }

  public JBCheckBox(@Nullable @NlsContexts.Checkbox String text) {
    this(text, false);
  }

  public JBCheckBox(@Nullable @NlsContexts.Checkbox String text, boolean selected) {
    super(text, null, selected);
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    if (this.myAnchor != anchor) {
      this.myAnchor = anchor;
      invalidate();
    }
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (myAnchor != null && myAnchor != this) {
      Dimension anchorSize = myAnchor.getPreferredSize();
      size.width = Math.max(size.width, anchorSize.width);
      size.height = Math.max(size.height, anchorSize.height);
    }
    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    if (myAnchor != null && myAnchor != this) {
      Dimension anchorSize = myAnchor.getMinimumSize();
      size.width = Math.max(size.width, anchorSize.width);
      size.height = Math.max(size.height, anchorSize.height);
    }
    return size;
  }

  /**
   * Sets given icon to display between checkbox icon and text.
   *
   * @return true in case of success and false otherwise
   */
  public boolean setTextIcon(@NotNull Icon icon) {
    if (StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
      return false;
    }
    ButtonUI ui = getUI();
    if (ui instanceof BasicRadioButtonUI) {
      Icon defaultIcon = ((BasicRadioButtonUI) ui).getDefaultIcon();
      if (defaultIcon != null) {
        MergedIcon mergedIcon = new MergedIcon(defaultIcon, 10, icon);
        setIcon(mergedIcon);
        return true;
      }
    }
    return false;
  }

  private static class MergedIcon implements Icon {

    private final Icon myLeftIcon;
    private final int myHorizontalStrut;
    private final Icon myRightIcon;

    MergedIcon(@NotNull Icon leftIcon, int horizontalStrut, @NotNull Icon rightIcon) {
      myLeftIcon = leftIcon;
      myHorizontalStrut = horizontalStrut;
      myRightIcon = rightIcon;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      paintIconAlignedCenter(c, g, x, y, myLeftIcon);
      paintIconAlignedCenter(c, g, x + myLeftIcon.getIconWidth() + myHorizontalStrut, y, myRightIcon);
    }

    private void paintIconAlignedCenter(Component c, Graphics g, int x, int y, @NotNull Icon icon) {
      int iconHeight = getIconHeight();
      icon.paintIcon(c, g, x, y + (iconHeight - icon.getIconHeight()) / 2);
    }

    @Override
    public int getIconWidth() {
      return myLeftIcon.getIconWidth() + myHorizontalStrut + myRightIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return Math.max(myLeftIcon.getIconHeight(), myRightIcon.getIconHeight());
    }
  }

}
