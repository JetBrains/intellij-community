// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.icons.CopyableIcon;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.Supplier;

public class IconWrapperWithToolTip implements IconWithToolTip, CopyableIcon, RetrievableIcon {
  private final Icon myIcon;
  private final Supplier<@NlsContexts.Tooltip String> myToolTip;

  public IconWrapperWithToolTip(Icon icon, Supplier<@NlsContexts.Tooltip String> toolTip) {
    myIcon = icon;
    myToolTip = toolTip;
  }

  @Contract(pure = true)
  protected IconWrapperWithToolTip(@NotNull IconWrapperWithToolTip another) {
    myIcon = another.myIcon;
    myToolTip = another.myToolTip;
  }

  @NotNull
  @Override
  public IconWrapperWithToolTip replaceBy(@NotNull IconReplacer replacer) {
    return new IconWrapperWithToolTip(replacer.replaceIcon(myIcon), myToolTip);
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
  public String getToolTip(boolean composite) {
    return myToolTip.get();
  }

  @Override
  public int hashCode() {
    return myIcon.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    return object == this ||
           object instanceof IconWrapperWithToolTip &&
           Objects.equals(((IconWrapperWithToolTip)object).myIcon, this.myIcon);
  }

  @Override
  public String toString() {
    return "IconWrapperWithTooltip:" + myIcon;
  }

  @Override
  public @NotNull Icon copy() {
    return new IconWrapperWithToolTip(myIcon, myToolTip);
  }

  @Override
  public @NotNull Icon retrieveIcon() {
    return myIcon;
  }
}

