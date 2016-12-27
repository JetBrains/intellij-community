/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.ColorUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Contains colors used in editor color scheme.
 */
public abstract class EditorColorPalette {
  private EditorColorsScheme myColorsScheme;
  private final Set<Color> myColors = new HashSet<>();

  public final static Comparator<Color> ORDER_NONE = Comparator.comparingInt(EditorColorPalette::getDefaultOrder);
  public final static Comparator<Color> ORDER_BY_INTENSITY = Comparator.comparingInt(EditorColorPalette::getIntensity);

  public EditorColorPalette(EditorColorsScheme colorsScheme) {
    myColorsScheme = colorsScheme;
  }
  
  public EditorColorPalette withBackgroundColors() {
    return collectColors(attr -> attr.getBackgroundColor());
  }
  
  public EditorColorPalette withForegroundColors() {
    return collectColors(attr -> attr.getForegroundColor());
  }
  
  private Collection<Color> orderBy(@Nullable Comparator<Color> comparator) {
    List<Color> sorted = new ArrayList<>();
    sorted.addAll(myColors);
    Collections.sort(sorted, comparator);
    return sorted;
  }
  
  private static int getIntensity(@NotNull Color color) {
    return (color.getRed() + color.getGreen() + color.getBlue()) / 3;
  }
  
  @SuppressWarnings("unused")
  private static int getDefaultOrder(@NotNull Color color) { return 0; }

  public Collection<Color> getColors(@NotNull Comparator<Color> comparator) {
    return comparator == ORDER_NONE ? myColors : orderBy(comparator);
  }

  private EditorColorPalette collectColors(@NotNull Function<TextAttributes, Color> attrColorReader) {
    Set<Color> colors = new HashSet<>();
    for (TextAttributesKey key : getTextAttributeKeys()) {
      TextAttributes attributes = myColorsScheme.getAttributes(key);
      if (attributes != null) {
        Color usedColor = attrColorReader.fun(attributes);
        if (usedColor != null) colors.add(usedColor);
      }
    }
    myColors.addAll(colors);
    return this;
  }

  /**
   * Search for a color which does not exist in the color palette by adjusting brightness of a given {@code sampleColor}. 
   * 
   * @param sampleColor The sample color to start with.
   * @return An adjusted color or the sample color if it doesn't conflict with the palette or {@code null} if non-conflicting
   *         color can't be found with used algorithms of brightness adjustment.
   */
  @Nullable
  public Color getClosestNonConflictingColor(@NotNull Color sampleColor) {
    boolean searchBrighter = ColorUtil.isDark(sampleColor);
    Color foundColor = getClosestNonConflictingColor(sampleColor, getAdjuster(searchBrighter));
    if (foundColor == null) {
      foundColor = getClosestNonConflictingColor(sampleColor, getAdjuster(!searchBrighter));
    }
    return foundColor;
  }
  
  private static Function<Color,Color> getAdjuster(boolean searchBrighter) {
    return searchBrighter ? Color::brighter : Color::darker;
  }

  /**
   * Search for a color which does not exist in the color palette using a given algorithm of color adjustment.
   * 
   * @param sampleColor   The sample color to start with.
   * @param colorAdjuster The color adjustment function, for example {@code Color:brighter}
   * @return An adjusted color or the sample color if it doesn't conflict with the palette or {@code null} if non-conflicting
   *         color can't be found with used algorithms of brightness adjustment.
   */
  @Nullable
  public Color getClosestNonConflictingColor(@NotNull Color sampleColor, @NotNull Function<Color,Color> colorAdjuster) {
    if (myColors.contains(sampleColor)) {
      Color newColor = colorAdjuster.fun(sampleColor);
      return !sampleColor.equals(newColor) ? getClosestNonConflictingColor(newColor, colorAdjuster) : null;
    }
    return sampleColor;
  }
  
  protected abstract Collection<TextAttributesKey> getTextAttributeKeys();
}
