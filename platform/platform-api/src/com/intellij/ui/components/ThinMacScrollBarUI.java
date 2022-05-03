// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import javax.swing.*;
import java.awt.*;

final class ThinMacScrollBarUI extends MacScrollBarUI {
  ThinMacScrollBarUI() {
    super(3, 3, 3);
  }

  @Override
  protected ScrollBarPainter.Thumb createThumbPainter() {
    return new ScrollBarPainter.ThinScrollBarThumb(() -> myScrollBar, false);
  }

  @Override
  void paintTrack(Graphics2D g, JComponent c) {
    // Track is not needed
  }
}