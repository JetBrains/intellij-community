// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import javax.swing.border.Border;
import java.awt.*;

public interface JBTabsBackgroundAndBorder extends Border {
  void setThickness(int value);
  int getThickness();
  Insets getEffectiveBorder();

  @Override
  default boolean isBorderOpaque() {
    return true;
  }
}
