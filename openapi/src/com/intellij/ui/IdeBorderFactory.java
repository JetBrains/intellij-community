/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class IdeBorderFactory {
  public static TitledBorder createTitledBorder(String title) {
    //return BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title);
    return BorderFactory.createTitledBorder(new RoundedLineBorder(Color.LIGHT_GRAY, 3), title);
  }

  public static Border createBorder() {
    //return BorderFactory.createEtchedBorder();
    return new RoundedLineBorder(Color.GRAY, 5);
  }

  public static Border createEmptyBorder(Insets insets) {
    return new EmptyBorder(insets);
  }

  public static Border createEmptyBorder(int top, int left, int bottom, int right) {
    return new EmptyBorder(top, left, bottom, right);
  }
}
