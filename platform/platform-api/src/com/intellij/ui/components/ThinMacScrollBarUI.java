// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public class ThinMacScrollBarUI extends MacScrollBarUI {
  public static final int DEFAULT_THICKNESS = 3;
  ThinMacScrollBarUI() {
    super(DEFAULT_THICKNESS, DEFAULT_THICKNESS, DEFAULT_THICKNESS);
  }


  public ThinMacScrollBarUI(int thickNess, int thicknessMax, int thicknessMin) {
   super(thickNess, thicknessMax, thicknessMin);
  }

  @Override
  protected ScrollBarPainter.Thumb createThumbPainter() {
    return new ScrollBarPainter.ThinScrollBarThumb(() -> myScrollBar, false);
  }

  @Override
  void paintTrack(Graphics2D g, JComponent c) {
    // Track is not needed
  }

  @Override
  protected @NotNull Insets getInsets(boolean small) {
    return JBUI.emptyInsets();
  }

  @Override
  protected void updateStyle(MacScrollBarUI.Style style) {
    super.updateStyle(Style.Overlay);
  }
}