// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.lw;

import javax.swing.*;

public class IconDescriptor {
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
