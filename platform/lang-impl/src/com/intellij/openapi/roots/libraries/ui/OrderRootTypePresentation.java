// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries.ui;

import javax.swing.*;

public final class OrderRootTypePresentation {
  private final String myNodeText;
  private final Icon myIcon;

  public OrderRootTypePresentation(String nodeText, Icon icon) {
    myNodeText = nodeText;
    myIcon = icon;
  }

  public String getNodeText() {
    return myNodeText;
  }

  public Icon getIcon() {
    return myIcon;
  }
}
