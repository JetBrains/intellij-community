// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class PaintAwarePanel extends JPanel {

  private @Nullable Consumer<? super Graphics> myPaintCallback;

  public PaintAwarePanel() {
    this(new GridBagLayout());
  }

  public PaintAwarePanel(LayoutManager layout) {
    super(layout);
  }

  @Override
  public void paint(Graphics g) {
    if (myPaintCallback != null) {
      myPaintCallback.consume(g);
    }
    super.paint(g);
  }

  public void setPaintCallback(@Nullable Consumer<? super Graphics> paintCallback) {
    myPaintCallback = paintCallback;
  }
}
