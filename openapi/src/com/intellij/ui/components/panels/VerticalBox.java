/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.components.panels;

import javax.swing.*;
import java.awt.*;

public class VerticalBox extends Box {

  public VerticalBox() {
    super(BoxLayout.Y_AXIS);
    setOpaque(false);
  }

  public Component add(Component comp) {
    ((JComponent) comp).setAlignmentX(0f);
    return super.add(comp);
  }
}
