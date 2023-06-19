// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.ColorUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Contains colors used in editor color scheme.
 */
public abstract class EditorColorPalette {
  protected final EditorColorsScheme myColorsScheme;
  private final MultiMap<Color, TextAttributesKey> myColors = new MultiMap<>();

  public static final Comparator<Color> ORDER_NONE = Comparator.comparingInt(EditorColorPalette::getDefaultOrder);
  public static final Comparator<Color> ORDER_BY_INTENSITY = Comparator.comparingInt(EditorColorPalette::getIntensity);

  public EditorColorPalette(EditorColorsScheme colorsScheme) {
    myColorsScheme = colorsScheme;
  }

  public EditorColorPalette withBackgroundColors() {
    return collectColors(attr -> attr.getBackgroundColor());
  }

  public EditorColorPalette withForegroundColors() {
    return collectColors(attr -> attr.getForegroundColor());
  }

  private Collection<Color> orderBy(@Nullable Comparator<? super Color> comparator) {
    List<Color> sorted = new ArrayList<>(myColors.keySet());
    sorted.sort(comparator);
    return sorted;
  }

  private static int getIntensity(@NotNull Color color) {
    return (color.getRed() + color.getGreen() + color.getBlue()) / 3;
  }

  @Contract(pure = true)
  private static int getDefaultOrder(@NotNull Color color) { return 0; }

  public Collection<Color> getColors(@NotNull Comparator<? super Color> comparator) {
    return comparator == ORDER_NONE ? myColors.keySet() : orderBy(comparator);
  }

  public @NotNull Set<Map.Entry<Color, Collection<TextAttributesKey>>> getEntries() {
    return myColors.entrySet();
  }

  /**
   * Collects colors from known color setup pages.
   *
   * @param attrColorReader the function to extract the color from attribute (ex. foreground or background)
   * @return the palette with collected colors
   */
  public EditorColorPalette collectColors(@NotNull Function<? super TextAttributes, ? extends Color> attrColorReader) {
    return collectColorsWithFilter(attrColorReader, false);
  }

  /**
   * Collects colors from known color setup pages.
   *
   * @param attrColorReader          the function to extract the color from attribute (ex. foreground or background)
   * @param filterOutRainbowAttrKeys the flag to filter out the attributes that can be overwritten by semantic highlighting
   *                                 or not conflicting with semantic highlighting
   * @return the palette with collected colors
   */
  public EditorColorPalette collectColorsWithFilter(@NotNull Function<? super TextAttributes, ? extends Color> attrColorReader,
                                                    boolean filterOutRainbowAttrKeys) {
    final MultiMap<Color, TextAttributesKey> colors = new MultiMap<>();
    for (TextAttributesKey key : getTextAttributeKeys(filterOutRainbowAttrKeys)) {
      TextAttributes attributes = myColorsScheme.getAttributes(key);
      if (attributes != null) {
        Color usedColor = attrColorReader.fun(attributes);
        if (usedColor != null) {
          colors.putValue(usedColor, key);
        }
      }
    }
    myColors.putAllValues(colors);
    return this;
  }

  /**
   * Search for a color which does not exist in the color palette by adjusting brightness of a given {@code sampleColor}.
   *
   * @param sampleColor The sample color to start with.
   * @return An adjusted color or the sample color if it doesn't conflict with the palette or {@code null} if non-conflicting
   * color can't be found with used algorithms of brightness adjustment.
   */
  public @Nullable Color getClosestNonConflictingColor(@NotNull Color sampleColor) {
    boolean searchBrighter = ColorUtil.isDark(sampleColor);
    Color foundColor = getClosestNonConflictingColor(sampleColor, getAdjuster(searchBrighter));
    if (foundColor == null) {
      foundColor = getClosestNonConflictingColor(sampleColor, getAdjuster(!searchBrighter));
    }
    return foundColor;
  }

  @Contract(pure = true)
  private static Function<Color, Color> getAdjuster(boolean searchBrighter) {
    return searchBrighter ? Color::brighter : Color::darker;
  }

  /**
   * Search for a color which does not exist in the color palette using a given algorithm of color adjustment.
   *
   * @param sampleColor   The sample color to start with.
   * @param colorAdjuster The color adjustment function, for example {@code Color:brighter}
   * @return An adjusted color or the sample color if it doesn't conflict with the palette or {@code null} if non-conflicting
   * color can't be found with used algorithms of brightness adjustment.
   */
  public @Nullable Color getClosestNonConflictingColor(@NotNull Color sampleColor, @NotNull Function<? super Color, ? extends Color> colorAdjuster) {
    if (myColors.containsKey(sampleColor)) {
      Color newColor = colorAdjuster.fun(sampleColor);
      return !sampleColor.equals(newColor) ? getClosestNonConflictingColor(newColor, colorAdjuster) : null;
    }
    return sampleColor;
  }

  protected abstract Collection<TextAttributesKey> getTextAttributeKeys(boolean filterRainbowAttrKeys);
}
