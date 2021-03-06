// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.icons.CopyableIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class IconWrapperWithToolTipComposite implements IconWithToolTip, CopyableIcon, RetrievableIcon {
  private final Icon myIcon;

  public IconWrapperWithToolTipComposite(Icon icon) {
    myIcon = icon;
  }

  @Override
  public @Nullable String getToolTip(boolean composite) {
    if (!composite) return null;
    return myIcon instanceof IconWithToolTip ? ((IconWithToolTip)myIcon).getToolTip(true) : null;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    myIcon.paintIcon(c, g, x, y);
  }

  @Override
  public int getIconWidth() {
    return myIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myIcon.getIconHeight();
  }

  @Override
  public @NotNull Icon copy() {
    return new IconWrapperWithToolTipComposite(myIcon);
  }

  @Override
  public @NotNull Icon retrieveIcon() {
    return myIcon;
  }

  @Override
  public int hashCode() {
    return myIcon.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    return object == this ||
           object instanceof IconWrapperWithToolTipComposite &&
           Objects.equals(((IconWrapperWithToolTipComposite)object).myIcon, this.myIcon);
  }

  @Override
  public String toString() {
    return "IconWrapperWithTooltipComposite:" + myIcon.toString();
  }
}
