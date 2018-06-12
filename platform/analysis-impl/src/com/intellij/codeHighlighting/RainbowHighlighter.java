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
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.SchemeMetaInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.*;
import java.util.List;

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
  private static final Set<TextAttributesKey> CODE_INSIGHT_CONFLICT_KEYS = new HashSet<>(Arrays.asList(
    CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES,
    CodeInsightColors.ERRORS_ATTRIBUTES,
    CodeInsightColors.WARNINGS_ATTRIBUTES,
    CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING,
    CodeInsightColors.DUPLICATE_FROM_SERVER,
    CodeInsightColors.WEAK_WARNING_ATTRIBUTES,
    CodeInsightColors.INFORMATION_ATTRIBUTES,
    CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES,
    CodeInsightColors.DEPRECATED_ATTRIBUTES,
    CodeInsightColors.MATCHED_BRACE_ATTRIBUTES,
    CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES,
    CodeInsightColors.JOIN_POINT,
    CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES,
    CodeInsightColors.HYPERLINK_ATTRIBUTES,
    CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES,
    CodeInsightColors.TODO_DEFAULT_ATTRIBUTES,
    CodeInsightColors.BOOKMARKS_ATTRIBUTES));

  static {
    for (int i = 0; i < RAINBOW_JB_COLORS_DEFAULT.length; ++i) {
      //noinspection deprecation
      RAINBOW_COLOR_KEYS[i] = TextAttributesKey.createTextAttributesKey("RAINBOW_COLOR" + i, createRainbowAttribute(RAINBOW_JB_COLORS_DEFAULT[i]));
    }
  }
  public static final String RAINBOW_TYPE = "rainbow";
  private static final String RAINBOW_TEMP_PREF = "RAINBOW_TEMP_";

  @SuppressWarnings("deprecation")
  public static final TextAttributesKey RAINBOW_ANCHOR = TextAttributesKey.createTextAttributesKey(RAINBOW_TYPE, new TextAttributes());
  @SuppressWarnings("deprecation")
  public static final TextAttributesKey RAINBOW_GRADIENT_DEMO = TextAttributesKey.createTextAttributesKey("rainbow_demo", new TextAttributes());
  public static final Boolean DEFAULT_RAINBOW_ON = Boolean.FALSE;

  @NotNull private final TextAttributesScheme myColorsScheme;
  @NotNull private final Color[] myRainbowColors;

  public RainbowHighlighter(@Nullable TextAttributesScheme colorsScheme) {
    myColorsScheme = colorsScheme != null ? colorsScheme : EditorColorsManager.getInstance().getGlobalScheme();
    myRainbowColors = generateColorSequence(myColorsScheme, colorsScheme != null);
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
    return RAINBOW_TYPE + " " + (language == null ? UIBundle.message("color.settings.common.default.language") : language.getID());
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isRainbowKey(@Nullable Object key) {
    return key instanceof String && ((String)key).startsWith(RAINBOW_TYPE);
  }

  public static void transferRainbowState(@NotNull SchemeMetaInfo dst, @NotNull SchemeMetaInfo src) {
    final Properties dstProps = dst.getMetaProperties();
    dstProps.entrySet().removeIf(entry -> isRainbowKey(entry.getKey()));
    src.getMetaProperties().forEach((Object key, Object value) -> {
      if (isRainbowKey(key) && value instanceof String) {
        dstProps.setProperty((String)key, (String)value);
      }
    });
  }

  @NotNull
  public static String generatePaletteExample(@NotNull String indent) {
    int stopCount = RAINBOW_COLOR_KEYS.length;
    StringBuilder sb = new StringBuilder();

    sb.append(indent).append(UIBundle.message("color.settings.rainbow.demo.header.1"))
      .append(indent).append(UIBundle.message("color.settings.rainbow.demo.header.2"))
      .append(indent);
    String tagRainbow = RAINBOW_GRADIENT_DEMO.getExternalName();
    boolean needLineBreak = true;
    for (int i = 0; i < RAINBOW_TEMP_KEYS.length; ++i) {
      sb.append(" ");
      sb.append("<").append(tagRainbow).append(">");
      final String anchor = String.valueOf(i / stopCount + 1);
      final String minor = String.valueOf(i % stopCount);
      sb.append((i % stopCount == 0) ? "Color#" + anchor : ("SC" + anchor + "." + minor));
      sb.append("</").append(tagRainbow).append(">");
      if (needLineBreak && i == RAINBOW_TEMP_KEYS.length / 2) {
        sb.append(indent);
        needLineBreak = false;
        //noinspection AssignmentToForLoopParameter
        --i;
      }
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
  private static Color[] generateColorSequence(@NotNull TextAttributesScheme colorsScheme, boolean withCorrectionAndCaching) {
    String colorDump = ApplicationManager.getApplication().isUnitTestMode()
                       ? UNIT_TEST_COLORS
                       : Registry.get("rainbow.highlighter.colors").asString();

    final List<String> registryColors = StringUtil.split(colorDump, ",");
    if (!registryColors.isEmpty()) {
      return registryColors.stream().map(s -> ColorUtil.fromHex(s.trim())).toArray(Color[]::new);
    }

    if (withCorrectionAndCaching) {
      Color[] colors = getColorsFromCache(colorsScheme);
      if (colors != null) {
        return colors;
      }
    }
    return generateColors(colorsScheme, withCorrectionAndCaching);
  }

  @NotNull
  @TestOnly
  public static Color[] testRainbowGenerateColors(@NotNull TextAttributesScheme colorsScheme) {
    return generateColors(colorsScheme, true);
  }

  @NotNull
  private static Color[] generateColors(@NotNull TextAttributesScheme colorsScheme, boolean withCorrectionAndCaching) {
    List<Color> stopRainbowColors =
      ContainerUtil.map(RAINBOW_COLOR_KEYS, key -> getRainbowColorFromAttribute(colorsScheme.getAttributes(key)));
    List<Color> rainbowColors = ColorGenerator.generateLinearColorSequence(stopRainbowColors, RAINBOW_COLORS_BETWEEN);

    if (withCorrectionAndCaching && (colorsScheme instanceof EditorColorsScheme)) {
      final EditorColorPalette palette = EditorColorPaletteFactory
        .getInstance()
        .getPalette((EditorColorsScheme)colorsScheme, Language.ANY)
        .collectColorsWithFilter(attr -> getRainbowColorFromAttribute(attr), true);


      final List<Pair<Color, Double>> colorCircles = new ArrayList<>();
      final Color background = ((EditorColorsScheme)colorsScheme).getDefaultBackground();
      final boolean schemeIsDark = ColorUtil.isDark(background);
      final double minDistanceWithOrdinal = schemeIsDark ? 0.06 : 0.10;      
      final double minDistanceWithDiagnostic = schemeIsDark ? 0.12 : 0.20;
      colorCircles.add(Pair.create(background, 0.24));
      
      palette.getEntries().forEach(entry -> colorCircles.add(Pair.create(entry.getKey(),
                                                                         Collections.disjoint(CODE_INSIGHT_CONFLICT_KEYS, entry.getValue())
                                                                         ? minDistanceWithOrdinal
                                                                         : minDistanceWithDiagnostic)));
      rainbowColors = ContainerUtil.map(rainbowColors, rainbowColor -> resolveConflict(colorCircles, rainbowColor, 0));
      int i = 0;
      for (Color rainbowColor : rainbowColors) {
        TextAttributesKey key = createRainbowKey(i++, rainbowColor);
        ((EditorColorsScheme)colorsScheme).setAttributes(key, key.getDefaultAttributes());
      }
    }
    return rainbowColors.toArray(new Color[0]);
  }

  private static Color resolveConflict(@NotNull final List<Pair<Color, Double>> colorCircles, @NotNull final Color sampleColor, int nestLevel) {
    if (nestLevel > 4) {
      return sampleColor;
    }
    for (Pair<Color, Double> circle: colorCircles) {
      final Color paletteColor = circle.first;
      final double distance = colorDistance01(sampleColor, paletteColor);
      if (distance < circle.second) {
        final float[] rgb = rgbDiffColor(sampleColor, paletteColor);
        final double factor = 256 * circle.second / getLength(rgb);
        final int mod = nestLevel % 4; 
        final int r = normalize(sampleColor.getRed() + rgb[0] * factor * (mod == 3 ? 2 : 1));          
        final int g = normalize(sampleColor.getGreen() + rgb[1] * factor * (mod == 1 ? 2 : 1));
        final int b = normalize(sampleColor.getBlue() + rgb[2] * factor * (mod == 2 ? 2 : 1));
        final float[] hsbNew = Color.RGBtoHSB(r, g, b, null);
        final float[] hsbOrig = Color.RGBtoHSB(sampleColor.getRed(), sampleColor.getGreen(), sampleColor.getBlue(), null);
        
        //System.out.println("#" + nestLevel + ":" + sampleColor + " diff:" + circle.second + " conflict:" + circle.first + " now:" +Color.getHSBColor(hsbNew[0], hsbNew[1], hsbOrig[2]));
        return resolveConflict(colorCircles,
                               Color.getHSBColor(hsbNew[0], hsbNew[1], (hsbOrig[2] + hsbNew[2])/2), 
                               ++nestLevel);
      }
    }
    return sampleColor;
  }

  private static int normalize(double b) {
    return Math.min(Math.max(1, (int)b), 254);
  }

  public static double colorDistance01(@NotNull Color c1, @NotNull Color c2) {
    return getLength(YPbPr01(rgbDiffColor(c1, c2)));
  }

  private static double getLength(@NotNull float[] components) {
    return Math.sqrt(components[0] * components[0] + components[1] * components[1] + components[2] * components[2]);
  }

  @NotNull
  private static float[] rgbDiffColor(@NotNull Color c1, @NotNull Color c2) {
    return new float[] {
      (float)(c1.getRed() - c2.getRed()),
      (float)(c1.getGreen() - c2.getGreen()),
      (float)(c1.getBlue() - c2.getBlue())
    };
  }

  @NotNull
  @Contract(pure = true)
  private static float[] YPbPr01(@NotNull float[] rgb) {
    // http://www.equasys.de/colorconversion.html
    return new float[]{
      (float)((  0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2])/256),
      (float)(( -0.169 * rgb[0] - 0.331 * rgb[1] + 0.500 * rgb[2])/256),
      (float)(( +0.500 * rgb[0] - 0.419 * rgb[1] - 0.081 * rgb[2])/256)
    };
  } 

  @Nullable
  private static Color[] getColorsFromCache(@NotNull TextAttributesScheme colorsScheme) {
    List<Color> colors = new ArrayList<>();
    boolean invalidCache = false;
    for (TextAttributesKey tempKey : RAINBOW_TEMP_KEYS) {
      final TextAttributes attributes = colorsScheme.getAttributes(tempKey);
      if (attributes == null) {
        invalidCache = true;
        break;
      }
      colors.add(getRainbowColorFromAttribute(attributes));
    }
    if (invalidCache) {
      return null;
    }
    return colors.toArray(new Color[0]);
  }

  @Nullable
  public static Color getRainbowColorFromAttribute(@NotNull TextAttributes attributes) {
    return attributes.getForegroundColor();
  }

  private static void setRainbowColorToAttribute(@NotNull TextAttributes attributes, @Nullable Color rainbowColor) {
    attributes.setForegroundColor(rainbowColor);
  }

  @NotNull
  private static TextAttributesKey createRainbowKey(int i, Color rainbowColor) {
    TextAttributesKey key = TextAttributesKey.createTempTextAttributesKey(
      RAINBOW_TEMP_PREF + i,
      new TextAttributes());
    key.getDefaultAttributes().setForegroundColor(rainbowColor);
    return key;
  }

  @NotNull
  public TextAttributesKey[] getRainbowTempKeys() {
    TextAttributesKey[] keys = new TextAttributesKey[myRainbowColors.length];
    for (int i = 0; i < myRainbowColors.length; ++i) {
      keys[i] = createRainbowKey(i, myRainbowColors[i]);
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
  public static TextAttributes createRainbowAttribute(@Nullable Color color) {
    TextAttributes ret = new TextAttributes();
    setRainbowColorToAttribute(ret, color);
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
