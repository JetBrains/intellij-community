/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.components.panels;

import javax.swing.*;
import java.awt.*;

public class HorizontalBox extends JPanel {

  private Box myBox;

  public HorizontalBox() {
    setLayout(new BorderLayout());
    myBox = new Box(BoxLayout.X_AXIS) {
      public Component add(Component comp) {
        ((JComponent) comp).setAlignmentY(0f);
        return super.add(comp);
      }
    };
    add(myBox, BorderLayout.CENTER);
    setOpaque(false);
  }

  public Component add(Component aComponent) {
    return myBox.add(aComponent);
  }

  public void remove(Component aComponent) {
    myBox.remove(aComponent);
  }

  public void removeAll() {
    myBox.removeAll();
  }

}
