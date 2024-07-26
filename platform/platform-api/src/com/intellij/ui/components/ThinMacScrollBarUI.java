// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public @NotNull ScrollBarPainter.Thumb createThumbPainter$intellij_platform_ide(@NotNull DefaultScrollbarUiInstalledState state) {
    return new ScrollBarPainter.ThinScrollBarThumb(() -> state.scrollBar, false, state.coroutineScope);
  }

  @Override
  public void paintTrack(@NotNull Graphics2D g, @NotNull JComponent c) {
    // Track is not needed
  }

  @Override
  protected @NotNull Insets getInsets(boolean small) {
    return JBUI.emptyInsets();
  }

  @Override
  protected void updateStyle(MacScrollbarStyle style) {
    super.updateStyle(MacScrollbarStyle.Overlay);
  }
}