// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ui.components.JBOptionButton;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class MyOptionButton extends JBOptionButton {
  public MyOptionButton(Action action, Action option) {
    super(action, new Action[]{option});
    ColorButton.setWidth72(this);
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);
    int count = getComponentCount();
    for (int i = 0; i < count; i++) {
      getComponent(i).setBackground(bg);
    }
  }

  @Override
  public int getBaseline(int width, int height) {
    if (getComponentCount() == 2) {
      Component component = getComponent(0);
      Dimension size = component.getPreferredSize();
      return component.getBaseline(size.width, size.height);
    }
    return super.getBaseline(width, height);
  }
}