// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import javax.swing.*;
import java.awt.*;

final class TextSearchRenderer extends JPanel implements ListCellRenderer<SearchEverywhereItem> {
  private final TextSearchListAgnosticRenderer myRenderer = new TextSearchListAgnosticRenderer((list, index) -> {
    if (index <= 0) return null;
    else if (list.getModel().getElementAt(index - 1) instanceof SearchEverywhereItem item) return item.getPresentation();
    else return null;
  });

  @Override
  public Component getListCellRendererComponent(JList<? extends SearchEverywhereItem> list,
                                                SearchEverywhereItem value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    return myRenderer.getListCellRendererComponent(list, value.getPresentation(), index, isSelected, cellHasFocus);
  }
}
