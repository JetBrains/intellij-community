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
package com.intellij.codeHighlighting;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.notNull;

public class RainbowHighlighter {
  private final static String UNIT_TEST_COLORS = "#000001,#000002,#000003,#000004"; // Do not modify!
  private final static int COLOR_COUNT = 16;

  @NotNull private final TextAttributesScheme myColorsScheme;
  @NotNull private final List<Color> myRainbowColors;

  public RainbowHighlighter(@Nullable TextAttributesScheme colorsScheme) {
    myColorsScheme = colorsScheme != null ? colorsScheme : EditorColorsManager.getInstance().getGlobalScheme();
    myRainbowColors = generateColorSequence(myColorsScheme);
  }

  public static final HighlightInfoType RAINBOW_ELEMENT =
    new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, DefaultLanguageHighlighterColors.CONSTANT);

  public static boolean isRainbowEnabled() {
    return Registry.is("editor.rainbow.identifiers", false);
  }

  @NotNull
  public Color calculateForeground(int colorIndex) {
    return myRainbowColors.get(Math.abs(colorIndex) % myRainbowColors.size());
  }

  public int getColorsCount() {
    return myRainbowColors.size();
  }

  private static List<Color> generateColorSequence(@NotNull TextAttributesScheme colorsScheme) {
    String colorDump = ApplicationManager.getApplication().isUnitTestMode()
      ? UNIT_TEST_COLORS
      : Registry.get("rainbow.highlighter.colors").asString();

    final List<String> registryColors = StringUtil.split(colorDump, ",");
    if (!registryColors.isEmpty()) {
      return registryColors.stream().map((s -> ColorUtil.fromHex(s.trim()))).collect(Collectors.toList());
    }

    List<Color> colors = new ArrayList<Color>(COLOR_COUNT);
    TextAttributes attributes = colorsScheme.getAttributes(DefaultLanguageHighlighterColors.CONSTANT);
    Color foregroundColor = notNull(attributes != null ? attributes.getForegroundColor() : null, JBColor.gray);
    float[] floats = Color.RGBtoHSB(foregroundColor.getRed(), foregroundColor.getGreen(), foregroundColor.getBlue(), null);
    for (int i = 0; i < COLOR_COUNT; ++i) {
      float factor = ((float)i) / COLOR_COUNT;
      Color color = Color.getHSBColor(factor, .7f, floats[2] * 1.3f);
      //noinspection UseJBColor
      colors.add(new Color(color.getRed()/2, color.getGreen(), color.getBlue())); // to highlight errors we reduce Red color
    }
    return colors;
  }

  public HighlightInfo getInfo(int colorIndex, @Nullable PsiElement id, @Nullable TextAttributesKey colorKey) {
    if (id == null) {
      return null;
    }
    if (colorKey == null) {
      colorKey = DefaultLanguageHighlighterColors.LOCAL_VARIABLE;
    }
    return HighlightInfo
      .newHighlightInfo(RAINBOW_ELEMENT)
      .textAttributes(TextAttributes
                        .fromFlyweight(myColorsScheme
                                         .getAttributes(colorKey)
                                         .getFlyweight()
                                         .withForeground(calculateForeground(colorIndex))))
      .range(id)
      .create();
  }
}
