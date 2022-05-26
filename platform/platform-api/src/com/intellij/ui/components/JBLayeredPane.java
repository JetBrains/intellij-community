// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.*;

public class JBLayeredPane extends JLayeredPane {
  @Override
  public Component add(Component comp, int index) {
    Logger.getInstance(JBLayeredPane.class)
      .warn("Probably incorrect call - constraint as primitive integer will be used as index", new Throwable());
    addImpl(comp, null, index);
    return comp;
  }

  @Override
  public Dimension getMinimumSize() {
    if (!isMinimumSizeSet())
      return new Dimension(0, 0);
    return super.getMinimumSize();
  }
}
