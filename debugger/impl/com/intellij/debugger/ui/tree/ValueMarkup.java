/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.tree;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 27, 2007
 */
public class ValueMarkup {
  private final Icon myIcon;
  private final Color myColor;

  public ValueMarkup(final Icon icon, final Color color) {
    myIcon = icon;
    myColor = color;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public Color getColor() {
    return myColor;
  }
}
