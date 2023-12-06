// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Used to override caret attributes such as shape, size or painting color.
 */
public final class CaretVisualAttributes {
  public static final CaretVisualAttributes DEFAULT = new CaretVisualAttributes(null, Weight.NORMAL, Shape.DEFAULT, 1.0f);

  private final @Nullable Color myColor;
  private final @NotNull Weight myWeight;
  private final @NotNull Shape myShape;
  private final float myThickness;

  /**
   * Creates an instance of {@link CaretVisualAttributes} using the {@link Shape#DEFAULT} shape.
   *
   * @param color The color to draw the caret. If {@code null}, the caret will be drawn using {@link com.intellij.openapi.editor.colors.EditorColors#CARET_COLOR}.
   * @param weight Describes the thickness of the caret when drawn as a vertical bar.
   */
  public CaretVisualAttributes(@Nullable Color color, @NotNull Weight weight) {
    this(color, weight, Shape.DEFAULT, 1.0f);
  }

  /**
   * Creates an instance of {@link CaretVisualAttributes} with full control of color, shape and caret size.
   *
   * @param color The color to draw the caret. If {@code null}, the caret will be drawn using {@link com.intellij.openapi.editor.colors.EditorColors#CARET_COLOR}.
   * @param weight The weight of the vertical bar. This is only used when shape is {@link Shape#DEFAULT}.
   * @param shape The shape to draw the caret.
   * @param thickness The thickness used to draw the caret, as a factor of height or width. The value is clamped between 0 and 1.0f
   *                  This only applies when shape is either {@link Shape#BAR} or {@link Shape#UNDERSCORE}.
   */
  public CaretVisualAttributes(@Nullable Color color, @NotNull Weight weight, @NotNull Shape shape, float thickness) {
    myColor = color;
    myWeight = weight;
    myShape = shape;
    myThickness = MathUtil.clamp(thickness, 0, 1.0f);
  }

  /**
   * @return The color used to paint the caret. If {@code null}, the caret will be drawn using {@link com.intellij.openapi.editor.colors.EditorColors#CARET_COLOR}.
   */
  public @Nullable Color getColor() {
    return myColor;
  }

  /**
   * @return The {@link Shape} to draw the caret.
   */
  public @NotNull Shape getShape() {
    return myShape;
  }

  /**
   * Thickness of a {@link Shape#BAR} or {@link Shape#UNDERSCORE} caret expressed as a factor of width or height. 
   *
   * The value is clamped between 0 and 1.0f. If the value is 0, the caret is effectively hidden. Any value greater than zero is subject to
   * a minimum line size. It might be useful for a plugin to hide the caret e.g. to visually emulate block selection as a selection rather
   * than as multiple carets with discrete selections.
   *
   * @return A factor to multiply against width or height.
   */
  public float getThickness() {
    return myThickness;
  }

  /**
   * The weight used to modify the width of the vertical bar when shape is {@link Shape#DEFAULT}.
   *
   * @return The {@link Weight} of the {@link Shape#DEFAULT} caret when drawn as a vertical bar, used to modify the thickness.
   */
  public @NotNull Weight getWeight() {
    return myWeight;
  }

  /**
   * Modifies the width of the {@link Shape#DEFAULT} caret when drawn as a vertical bar, according to {@link #getWeight}.
   *
   * @param defaultWidth The width that would be used to draw the bar, before applying weight.
   * @return The width of the vertical bar caret to draw, after applying weight.
   */
  public int getWidth(int defaultWidth) {
    return Math.max(1, defaultWidth + myWeight.delta);
  }

  public enum Weight {
    THIN(-1),
    NORMAL(0),
    HEAVY(1);

    private final int delta;

    Weight(int delta) {
      this.delta = delta;
    }

    public int getDelta() {
      return delta;
    }
  }

  public enum Shape {
    /**
     * Use the default caret shape. This is either block or vertical bar, depending on user settings and insert/overwrite mode.
     */
    DEFAULT,

    /**
     * Use a block shape.
     */
    BLOCK,

    /**
     * Use a vertical bar caret. The thickness of the caret comes from {@link #getThickness()}.
     */
    BAR,

    /**
     * Use an underscore caret. The thickness of the caret comes from {@link #getThickness()}.
     */
    UNDERSCORE,

    /**
     * Use a box caret. This is like the block caret, but is drawn as an outline.
     */
    BOX
  }
}
