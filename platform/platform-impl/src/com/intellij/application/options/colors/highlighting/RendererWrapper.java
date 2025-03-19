// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.highlighting;

import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.openapi.editor.colors.CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES;

public final class RendererWrapper implements EditorCustomElementRenderer {
  private final EditorCustomElementRenderer myDelegate;
  private boolean myDrawBorder;

  public RendererWrapper(EditorCustomElementRenderer delegate, boolean drawBorder) {
    myDelegate = delegate;
    myDrawBorder = drawBorder;
  }

  @Override
  public int calcWidthInPixels(@NotNull Inlay inlay) {
    return myDelegate.calcWidthInPixels(inlay);
  }

  @Override
  public int calcHeightInPixels(@NotNull Inlay inlay) {
    return myDelegate.calcHeightInPixels(inlay);
  }

  @Override
  public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
    myDelegate.paint(inlay, g, r, textAttributes);
    if (myDrawBorder) {
      TextAttributes attributes = inlay.getEditor().getColorsScheme().getAttributes(BLINKING_HIGHLIGHTS_ATTRIBUTES);
      if (attributes != null && attributes.getEffectColor() != null) {
        g.setColor(attributes.getEffectColor());
        g.drawRect(r.x, r.y, r.width, r.height);
      }
    }
  }
}
