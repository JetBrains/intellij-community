// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TextHorizontalLayout extends HorizontalLayout {
  public static final String FIX_LABEL = "fix_label";

  private JLabel myFixLabel;

  public TextHorizontalLayout(int offset) {
    super(offset);
  }

  @Override
  public void addLayoutComponent(Component component, Object constraints) {
    if (FIX_LABEL.equals(constraints)) {
      myFixLabel = (JLabel)component;
    }
    super.addLayoutComponent(component, constraints);
  }

  @Override
  public void layoutContainer(Container parent) {
    super.layoutContainer(parent);

    if (myFixLabel != null) {
      if (parent.getWidth() < myFixLabel.getX() + myFixLabel.getWidth()) {
        myFixLabel.setToolTipText(myFixLabel.getText());
        myFixLabel.setSize(parent.getWidth() - myFixLabel.getX(), myFixLabel.getHeight());
      }
      else {
        myFixLabel.setToolTipText(null);
      }
    }
  }
}