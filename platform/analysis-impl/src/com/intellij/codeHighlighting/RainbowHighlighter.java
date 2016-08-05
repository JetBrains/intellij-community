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
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RainbowHighlighter {
  private final static int[] RAINBOW_COLORS_DEFAULT = {0x9b3b6a, 0x114d77, 0xbc8650, 0x005910, 0xbc5150};
  private final static int[] RAINBOW_COLORS_DARK = {0x529d52, 0xbe7070, 0x3d7676, 0xbe9970, 0x9d527c};
  private final static int RAINBOW_COLORS_BETWEEN = 4;
  private final static List<TextAttributesKey> RAINBOW_COLOR_KEYS = new ArrayList<TextAttributesKey>(RAINBOW_COLORS_DEFAULT.length);
  private final static String UNIT_TEST_COLORS = "#000001,#000002,#000003,#000004"; // Do not modify!

  @NotNull private final TextAttributesScheme myColorsScheme;
  @NotNull private final List<Color> myRainbowColors;
  public final static String RAINBOW_TYPE = "rainbow";
  private final static String RAINBOW_TEMP_PREF = "RAINBOW_TEMP_";

  public RainbowHighlighter(@Nullable TextAttributesScheme colorsScheme) {
    myColorsScheme = colorsScheme != null ? colorsScheme : EditorColorsManager.getInstance().getGlobalScheme();
    myRainbowColors = generateColorSequence(myColorsScheme);
  }

  public static final HighlightInfoType RAINBOW_ELEMENT =
    new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, DefaultLanguageHighlighterColors.CONSTANT);

  public static boolean isRainbowEnabled() {
    return Registry.is("editor.rainbow.identifiers", false);
  }

  public static void setRainbowEnabled(boolean enabled) {
    Registry.get("editor.rainbow.identifiers").setValue(enabled);
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
      return registryColors.stream().map(s -> ColorUtil.fromHex(s.trim())).collect(Collectors.toList());
    }

    return ColorGenerator.generateLinearColorSequence(getRainbowKeys()
                                                        .stream()
                                                        .map(key ->
                                                               colorsScheme.getAttributes(key).getForegroundColor()
                                                        )
                                                        .collect(Collectors.toList()),
                                                      RAINBOW_COLORS_BETWEEN);
  }

  public static synchronized List<TextAttributesKey> getRainbowKeys() {
    if (RAINBOW_COLOR_KEYS.isEmpty()) {
      for (int i = 0; i < RAINBOW_COLORS_DEFAULT.length; ++i) {
        RAINBOW_COLOR_KEYS.add(TextAttributesKey.createTextAttributesKey("RAINBOW_COLOR" + i,
                                                                         new TextAttributes(
                                                                           new JBColor(RAINBOW_COLORS_DEFAULT[i],
                                                                                       RAINBOW_COLORS_DARK[i]),
                                                                           null, null, null, Font.PLAIN)));
      }
    }
    return RAINBOW_COLOR_KEYS;
  }

  public static int getRainbowHash(@NotNull String id) {
    return StringHash.murmur(id, 0x55AA);
  }

  @NotNull
  public List<TextAttributesKey> getRainbowTempKeys() {
    int[] index = {0};
    return myRainbowColors
        .stream()
        .map(color -> {
          TextAttributesKey key = TextAttributesKey.createTextAttributesKey(RAINBOW_TEMP_PREF + index[0]++, new TextAttributes());
          key.getDefaultAttributes().setForegroundColor(color);
          return key;
        })
        .collect(Collectors.toList());
  }

  public static boolean isRainbowTempKey(TextAttributesKey key) {
    return key.getExternalName().startsWith(RAINBOW_TEMP_PREF);
  }

  public HighlightInfo getInfo(int colorIndex, @Nullable PsiElement id, @Nullable TextAttributesKey colorKey) {
    return id == null ? null : getInfoBuilder(colorIndex, colorKey).range(id).create();
  }

  public HighlightInfo getInfo(int colorIndex, int start, int end, @Nullable TextAttributesKey colorKey) {
    return getInfoBuilder(colorIndex, colorKey).range(start, end).create();
  }

  public int getColorIndex(HashMap<String, Integer> id2index, @NotNull String id, int idHash) {
    Integer colorIndex = id2index.get(id);
    if (colorIndex == null) {
      colorIndex = Math.abs(idHash);

      Map<Integer, Integer> index2usage = new HashMap<Integer, Integer>();
      id2index.values().forEach(i -> {
        Integer useCount = index2usage.get(i);
        index2usage.put(i, useCount == null ? 1 : ++useCount);
      });

      int colorsCount = getColorsCount();
      out:
      for (int cutoff = 0; ; ++cutoff) {
        for (int i = 0; i < colorsCount; ++i) {
          colorIndex %= colorsCount;
          Integer useCount = index2usage.get(colorIndex % colorsCount);
          if (useCount == null) useCount = 0;
          if (useCount == cutoff) break out;
          ++colorIndex;
        }
      }
      id2index.put(id, colorIndex);
    }
    return colorIndex;
  }

  @NotNull
  protected HighlightInfo.Builder getInfoBuilder(int colorIndex, @Nullable TextAttributesKey colorKey) {
    if (colorKey == null) {
      colorKey = DefaultLanguageHighlighterColors.LOCAL_VARIABLE;
    }
    return HighlightInfo
      .newHighlightInfo(RAINBOW_ELEMENT)
      .textAttributes(TextAttributes
                        .fromFlyweight(myColorsScheme
                                         .getAttributes(colorKey)
                                         .getFlyweight()
                                         .withForeground(calculateForeground(colorIndex))));
  }
}
