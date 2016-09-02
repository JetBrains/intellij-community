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
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.SchemeMetaInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class RainbowHighlighter {
  private static final JBColor[] RAINBOW_JB_COLORS_DEFAULT = {
    new JBColor(0x9b3b6a, 0x529d52),
    new JBColor(0x114d77, 0xbe7070),
    new JBColor(0xbc8650, 0x3d7676),
    new JBColor(0x005910, 0xbe9970),
    new JBColor(0xbc5150, 0x9d527c),
  };
  public static final TextAttributesKey[] RAINBOW_COLOR_KEYS = new TextAttributesKey[RAINBOW_JB_COLORS_DEFAULT.length];
  private static final int RAINBOW_COLORS_BETWEEN = 4;
  private static final String UNIT_TEST_COLORS = "#000001,#000002,#000003,#000004"; // Do not modify!
  private static final String INHERITED = "inherited";

  static {
    for (int i = 0; i < RAINBOW_JB_COLORS_DEFAULT.length; ++i) {
      //noinspection deprecation
      RAINBOW_COLOR_KEYS[i] = TextAttributesKey.createTextAttributesKey("RAINBOW_COLOR" + i, createRainbowAttribute(RAINBOW_JB_COLORS_DEFAULT[i]));
    }
  }
  public final static String RAINBOW_TYPE = "rainbow";
  private final static String RAINBOW_TEMP_PREF = "RAINBOW_TEMP_";

  @SuppressWarnings("deprecation")
  public final static TextAttributesKey RAINBOW_ANCHOR = TextAttributesKey.createTextAttributesKey(RAINBOW_TYPE, new TextAttributes());
  @SuppressWarnings("deprecation")
  public final static TextAttributesKey RAINBOW_GRADIENT_DEMO = TextAttributesKey.createTextAttributesKey("rainbow_demo", new TextAttributes());
  public final static Boolean DEFAULT_RAINBOW_ON = Boolean.FALSE;

  @NotNull private final TextAttributesScheme myColorsScheme;
  @NotNull private final Color[] myRainbowColors;

  public RainbowHighlighter(@Nullable TextAttributesScheme colorsScheme) {
    myColorsScheme = colorsScheme != null ? colorsScheme : EditorColorsManager.getInstance().getGlobalScheme();
    myRainbowColors = generateColorSequence(myColorsScheme);
  }

  public static final HighlightInfoType RAINBOW_ELEMENT = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, DefaultLanguageHighlighterColors.CONSTANT);

  @Nullable
  @Contract("_, null -> !null")
  public static Boolean isRainbowEnabled(@Nullable TextAttributesScheme colorsScheme, @Nullable Language language) {
    if (colorsScheme instanceof SchemeMetaInfo) {
      String value = ((SchemeMetaInfo)colorsScheme).getMetaProperties().getProperty(getKey(language), INHERITED);
      if (String.valueOf(true).equals(value)) return Boolean.TRUE;
      if (String.valueOf(false).equals(value)) return Boolean.FALSE;
      return language == null ? DEFAULT_RAINBOW_ON : null;
    }
    return false;
  }

  public static boolean isRainbowEnabledWithInheritance(@Nullable TextAttributesScheme colorsScheme, @Nullable Language language) {
    Boolean rainbowEnabled = isRainbowEnabled(colorsScheme, language);
    return rainbowEnabled != null ? rainbowEnabled : isRainbowEnabled(colorsScheme, null);
  }

  public static void setRainbowEnabled(@NotNull SchemeMetaInfo colorsScheme, @Nullable Language language, @Nullable Boolean enabled) {
    Properties properties = colorsScheme.getMetaProperties();
    String key = getKey(language);
    if (enabled == null || (language == null && enabled == DEFAULT_RAINBOW_ON)) {
      properties.remove(key);
    }
    else {
      properties.setProperty(key, String.valueOf(enabled));
    }
  }

  @NotNull
  private static String getKey(@Nullable Language language) {
    return RAINBOW_TYPE + " " + (language == null ? "Default language" : language.getID());
  }

  @NotNull
  public static String generatePaletteExample() {
    int stopCount = RAINBOW_COLOR_KEYS.length;
    StringBuilder sb = new StringBuilder();
    String tagRainbow = RAINBOW_GRADIENT_DEMO.getExternalName();
    for (int i = 0; i < RAINBOW_TEMP_KEYS.length; ++i) {
      if (sb.length() != 0) {
        sb.append(" ");
      }
      sb.append("<").append(tagRainbow).append(">");
      sb.append((i % stopCount == 0) ? "Stop#" + String.valueOf(i / stopCount + 1) : "T");
      sb.append("</").append(tagRainbow).append(">");
    }
    return sb.toString();
  }

  @NotNull
  @Contract(pure = true)
  private Color calculateForeground(int colorIndex) {
    return myRainbowColors[colorIndex];
  }

  public int getColorsCount() {
    return myRainbowColors.length;
  }

  @NotNull
  private static Color[] generateColorSequence(@NotNull TextAttributesScheme colorsScheme) {
    String colorDump = ApplicationManager.getApplication().isUnitTestMode()
                       ? UNIT_TEST_COLORS
                       : Registry.get("rainbow.highlighter.colors").asString();

    final List<String> registryColors = StringUtil.split(colorDump, ",");
    if (!registryColors.isEmpty()) {
      return registryColors.stream().map(s -> ColorUtil.fromHex(s.trim())).toArray(Color[]::new);
    }

    List<Color> stopColors = ContainerUtil.map(RAINBOW_COLOR_KEYS, key -> colorsScheme.getAttributes(key).getForegroundColor());
    List<Color> colors = ColorGenerator.generateLinearColorSequence(stopColors, RAINBOW_COLORS_BETWEEN);
    return colors.toArray(new Color[colors.size()]);
  }

  @NotNull
  public TextAttributesKey[] getRainbowTempKeys() {
    TextAttributesKey[] keys = new TextAttributesKey[myRainbowColors.length];
    for (int i = 0; i < myRainbowColors.length; ++i) {
      //noinspection deprecation
      TextAttributesKey key = TextAttributesKey.createTextAttributesKey(RAINBOW_TEMP_PREF + i, new TextAttributes());
      key.getDefaultAttributes().setForegroundColor(myRainbowColors[i]);
      keys[i] = key;
    }
    return keys;
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

  private static final TextAttributesKey[] RAINBOW_TEMP_KEYS = new RainbowHighlighter(null).getRainbowTempKeys();

  @NotNull
  public static  TextAttributes createRainbowAttribute(@Nullable Color color) {
    TextAttributes ret = new TextAttributes();
    ret.setForegroundColor(color);
    return ret;
  }

  public static Map<String, TextAttributesKey> createRainbowHLM() {
    Map<String, TextAttributesKey> hashMap = new HashMap<>();
    hashMap.put(RAINBOW_ANCHOR.getExternalName(), RAINBOW_ANCHOR);
    hashMap.put(RAINBOW_GRADIENT_DEMO.getExternalName(), RAINBOW_GRADIENT_DEMO);
    for (TextAttributesKey key : RAINBOW_TEMP_KEYS) {
      hashMap.put(key.getExternalName(), key);
    }
    return hashMap;
  }
}
