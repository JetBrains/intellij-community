// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.text.AttributedCharacterIterator;

/**
 * @author Konstantin Bulenkov
 */
public final class Grayer extends Graphics2DDelegate {
  private final Color myBackground;
  private Color originalColor;

  public Grayer(Graphics2D g2d, Color background) {
    super(g2d);
    myBackground = background;
  }

  @Override
  public void setColor(Color color) {
    originalColor = color;
    if (color != null && !myBackground.equals(color)) {
      //noinspection UseJBColor
      color = new Color(UIUtil.getGrayFilter().filterRGB(0, 0, color.getRGB()));
    }
    super.setColor(color);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, float x, float y) {
    setTextColor();
    super.drawString(iterator, x, y);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, int x, int y) {
    setTextColor();
    super.drawString(iterator, x, y);
  }

  @Override
  public void drawString(String s, float x, float y) {
    setTextColor();
    super.drawString(s, x, y);
  }

  @Override
  public void drawString(String str, int x, int y) {
    setTextColor();
    super.drawString(str, x, y);
  }

  @Override
  public void drawChars(char data[], int offset, int length, int x, int y) {
    setTextColor();
    super.drawChars(data, offset, length, x, y);
  }

  private void setTextColor() {
    if (originalColor != null && !myBackground.equals(originalColor)) {
      //noinspection UseJBColor
      super.setColor(new Color(UIUtil.getTextGrayFilter().filterRGB(0, 0, originalColor.getRGB())));
    }
  }

  @Override
  public @NotNull Graphics create() {
    return new Grayer((Graphics2D)super.create(), myBackground);
  }
}
