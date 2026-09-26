// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.ui.WidthBasedLayout;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.JPanel;
import java.awt.Component;

@ApiStatus.Internal
public final class CombinedPopupPanel extends JPanel implements WidthBasedLayout {

  public CombinedPopupPanel(CombinedPopupLayout layout) {
    super(layout);
  }

  @Override
  public int getPreferredWidth() {
    return getPreferredSize().width;
  }

  @Override
  public int getPreferredHeight(int width) {
    int height = 0;
    for (Component c: getComponents()) {
      height += WidthBasedLayout.getPreferredHeight(c, width);
    }
    return height;
  }
}
