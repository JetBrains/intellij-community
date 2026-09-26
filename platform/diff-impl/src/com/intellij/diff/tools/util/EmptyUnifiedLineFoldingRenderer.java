// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util;

import com.intellij.openapi.CustomFoldRegionRendererEx;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

final class EmptyUnifiedLineFoldingRenderer implements CustomFoldRegionRendererEx {
  @Override
  public int getMinimumHeightInPixels() {
    return 0;
  }

  @Override
  public int calcWidthInPixels(@NotNull CustomFoldRegion region) {
    return 0;
  }

  @Override
  public int calcHeightInPixels(@NotNull CustomFoldRegion region) {
    return 0;
  }

  @Override
  public void paint(@NotNull CustomFoldRegion region,
                    @NotNull Graphics2D g,
                    @NotNull Rectangle2D targetRegion,
                    @NotNull TextAttributes textAttributes) {

  }
}
