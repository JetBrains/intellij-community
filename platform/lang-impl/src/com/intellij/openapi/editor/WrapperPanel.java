// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.ui.WidthBasedLayout;

import javax.swing.*;
import java.awt.*;

final class WrapperPanel extends JPanel implements WidthBasedLayout {

  WrapperPanel(JComponent content) {
    super(new BorderLayout());
    setBorder(null);
    setContent(content);
  }

  void setContent(JComponent content) {
    removeAll();
    add(content, BorderLayout.CENTER);
  }

  private JComponent getComponent() {
    return (JComponent)getComponent(0);
  }

  @Override
  public int getPreferredWidth() {
    return WidthBasedLayout.getPreferredWidth(getComponent());
  }

  @Override
  public int getPreferredHeight(int width) {
    return WidthBasedLayout.getPreferredHeight(getComponent(), width);
  }
}
