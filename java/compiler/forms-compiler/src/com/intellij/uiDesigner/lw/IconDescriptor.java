// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import javax.swing.*;

public final class IconDescriptor {
  private final String myIconPath;
  private Icon myIcon;

  public IconDescriptor(final String iconPath) {
    myIconPath = iconPath;
  }

  public String getIconPath() {
    return myIconPath;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public void setIcon(final Icon icon) {
    myIcon = icon;
  }
}
