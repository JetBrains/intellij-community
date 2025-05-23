// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeHighlighting;

import com.intellij.analysis.AnalysisBundle;
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
import com.intellij.util.MathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;
import java.util.*;

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
  private static final Set<TextAttributesKey> CODE_INSIGHT_CONFLICT_KEYS = Set.of(
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
    CodeInsightColors.BOOKMARKS_ATTRIBUTES);

  private static final TextAttributesKey[] RAINBOW_TEMP_KEYS;
  private static final Map<String, TextAttributesKey> RAINBOW_TEMP_TAG_TO_KEY_MAP;

  public static final String RAINBOW_TYPE = "rainbow";
  private static final String RAINBOW_TEMP_PREF = "RAINBOW_TEMP_";

  static {
    for (int i = 0; i < RAINBOW_JB_COLORS_DEFAULT.length; ++i) {
      //noinspection deprecation
      RAINBOW_COLOR_KEYS[i] = TextAttributesKey.createTextAttributesKey("RAINBOW_COLOR" + i, createRainbowAttribute(RAINBOW_JB_COLORS_DEFAULT[i]));
    }

    // pre-create TEMP TextAttributeKeys to avoid re-creating them with conflicting attributes later
    @SuppressWarnings("UseJBColor") List<Color> stopRainbowColors = Collections.nCopies(RAINBOW_COLOR_KEYS.length, Color.red);
    List<Color> rainbowColors = ColorGenerator.generateLinearColorSequence(stopRainbowColors, RAINBOW_COLORS_BETWEEN);
    TextAttributesKey[] keys = new TextAttributesKey[rainbowColors.size()];
    Map<String, TextAttributesKey> map = new HashMap<>();
    for (int i = 0; i < keys.length; ++i) {
      TextAttributesKey key = TextAttributesKey.createTempTextAttributesKey(RAINBOW_TEMP_PREF + i, null);
      keys[i] = key;
      map.put(key.getExternalName(), key);
    }
    RAINBOW_TEMP_KEYS = keys;
    RAINBOW_TEMP_TAG_TO_KEY_MAP = map;
  }


  public static final TextAttributesKey RAINBOW_ANCHOR = TextAttributesKey.createTextAttributesKey(RAINBOW_TYPE);
  public static final TextAttributesKey RAINBOW_GRADIENT_DEMO = TextAttributesKey.createTextAttributesKey("rainbow_demo");
  public static final Boolean DEFAULT_RAINBOW_ON = Boolean.FALSE;

  private final @NotNull TextAttributesScheme myColorsScheme;
  private final Color @NotNull [] myRainbowColors;

  public RainbowHighlighter(@NotNull TextAttributesScheme colorsScheme) {
    myColorsScheme = colorsScheme;
    myRainbowColors = generateColorSequence(myColorsScheme);
  }

  public static final HighlightInfoType RAINBOW_ELEMENT = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, DefaultLanguageHighlighterColors.CONSTANT);

  @Contract("_, null -> !null")
  public static @Nullable Boolean isRainbowEnabled(@Nullable TextAttributesScheme colorsScheme, @Nullable Language language) {
    if (colorsScheme instanceof SchemeMetaInfo metaInfo) {
      do {
        String value = metaInfo.getMetaProperties().getProperty(getKey(language), INHERITED);
        if (String.valueOf(true).equals(value)) return Boolean.TRUE;
        if (String.valueOf(false).equals(value)) return Boolean.FALSE;
        if (language == null) return DEFAULT_RAINBOW_ON;
        language = language.getBaseLanguage();
      }
      while (language != null);
      return null;
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
    if (enabled == null || language == null && enabled == DEFAULT_RAINBOW_ON) {
      properties.remove(key);
    }
    else {
      properties.setProperty(key, String.valueOf(enabled));
    }
  }

  private static @NotNull String getKey(@Nullable Language language) {
    return RAINBOW_TYPE + " " + (language == null ? AnalysisBundle.message("color.settings.common.default.language") : language.getID());
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isRainbowKey(@Nullable Object key) {
    return key instanceof String string && string.startsWith(RAINBOW_TYPE);
  }

  public static void transferRainbowState(@NotNull SchemeMetaInfo dst, @NotNull SchemeMetaInfo src) {
    Properties dstProps = dst.getMetaProperties();
    dstProps.entrySet().removeIf(entry -> isRainbowKey(entry.getKey()));
    src.getMetaProperties().forEach((Object key, Object value) -> {
      if (isRainbowKey(key) && value instanceof String string) {
        dstProps.setProperty((String)key, string);
      }
    });
  }

  public static @NotNull String generatePaletteExample(@NotNull String indent) {
    int stopCount = RAINBOW_COLOR_KEYS.length;
    StringBuilder sb = new StringBuilder();

    sb.append(indent).append(AnalysisBundle.message("color.settings.rainbow.demo.header.1"))
      .append(indent).append(AnalysisBundle.message("color.settings.rainbow.demo.header.2"))
      .append(indent);
    String tagRainbow = RAINBOW_GRADIENT_DEMO.getExternalName();
    boolean needLineBreak = true;
    for (int i = 0; i < RAINBOW_TEMP_KEYS.length; ++i) {
      sb.append(" ");
      sb.append("<").append(tagRainbow).append(">");
      String anchor = String.valueOf(i / stopCount + 1);
      String minor = String.valueOf(i % stopCount);
      sb.append(i % stopCount == 0 ? "Color#" + anchor : "SC" + anchor + "." + minor);
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

  public int getColorsCount() {
    return myRainbowColors.length;
  }

  private static Color @NotNull [] generateColorSequence(@NotNull TextAttributesScheme colorsScheme) {
    String colorDump = ApplicationManager.getApplication().isUnitTestMode()
                       ? UNIT_TEST_COLORS
                       : Registry.get("rainbow.highlighter.colors").asString();

    List<String> registryColors = StringUtil.split(colorDump, ",");
    if (!registryColors.isEmpty()) {
      return registryColors.stream().map(s -> ColorUtil.fromHex(s.trim())).toArray(Color[]::new);
    }

    Color[] colors = getColorsFromCache(colorsScheme);
    if (colors != null) {
      return colors;
    }
    return generateColors(colorsScheme);
  }

  @Contract(pure = true)
  public static @NotNull TextAttributesKey getRainbowAttrWithLazyCreation(@NotNull TextAttributesScheme colorsScheme, int colorIndex) {
    TextAttributesKey rainbowTempKey = RAINBOW_TEMP_KEYS[colorIndex % RAINBOW_TEMP_KEYS.length];
    if (colorsScheme.getAttributes(rainbowTempKey) == null) {
      // lazy rainbow keys init
      resetRainbowGeneratedColors(colorsScheme);
    }
    return rainbowTempKey;
  }

  public static void resetRainbowGeneratedColors(@NotNull TextAttributesScheme colorsScheme) {
    generateColors(colorsScheme);
  }

  @TestOnly
  public static Color @NotNull [] testRainbowGenerateColors(@NotNull TextAttributesScheme colorsScheme) {
    return generateColors(colorsScheme);
  }

  private static Color @NotNull [] generateColors(@NotNull TextAttributesScheme colorsScheme) {
    List<Color> stopRainbowColors =
      ContainerUtil.map(RAINBOW_COLOR_KEYS, key -> getRainbowColorFromAttribute(colorsScheme.getAttributes(key)));
    List<Color> rainbowColors = ColorGenerator.generateLinearColorSequence(stopRainbowColors, RAINBOW_COLORS_BETWEEN);

    if (colorsScheme instanceof EditorColorsScheme editorColorsScheme) {
      EditorColorPalette palette = EditorColorPaletteFactory
        .getInstance()
        .getPalette(editorColorsScheme, Language.ANY)
        .collectColorsWithFilter(attr -> getRainbowColorFromAttribute(attr), true);


      List<Pair<Color, Double>> colorCircles = new ArrayList<>();
      Color background = editorColorsScheme.getDefaultBackground();
      boolean schemeIsDark = ColorUtil.isDark(background);
      double minDistanceWithOrdinal = schemeIsDark ? 0.06 : 0.10;
      double minDistanceWithDiagnostic = schemeIsDark ? 0.12 : 0.20;
      colorCircles.add(Pair.create(background, 0.24));
      
      palette.getEntries().forEach(entry -> colorCircles.add(Pair.create(entry.getKey(),
                                                                         Collections.disjoint(CODE_INSIGHT_CONFLICT_KEYS, entry.getValue())
                                                                         ? minDistanceWithOrdinal
                                                                         : minDistanceWithDiagnostic)));
      rainbowColors = ContainerUtil.map(rainbowColors, rainbowColor -> resolveConflict(colorCircles, rainbowColor, 0));
      for (int i = 0; i < RAINBOW_TEMP_KEYS.length; i++) {
        TextAttributesKey key = RAINBOW_TEMP_KEYS[i];
        TextAttributes attributes = createRainbowAttribute(rainbowColors.get(i));
        editorColorsScheme.setAttributes(key, attributes);
      }
    }
    return rainbowColors.toArray(new Color[0]);
  }

  private static Color resolveConflict(@NotNull List<? extends Pair<Color, Double>> colorCircles, @NotNull Color sampleColor, int nestLevel) {
    if (nestLevel > 4) {
      return sampleColor;
    }
    for (Pair<Color, Double> circle: colorCircles) {
      Color paletteColor = circle.first;
      double distance = colorDistance01(sampleColor, paletteColor);
      if (distance < circle.second) {
        float[] rgb = rgbDiffColor(sampleColor, paletteColor);
        double factor = 256 * circle.second / getLength(rgb);
        int mod = nestLevel % 4;
        int r = normalize(sampleColor.getRed() + rgb[0] * factor * (mod == 3 ? 2 : 1));
        int g = normalize(sampleColor.getGreen() + rgb[1] * factor * (mod == 1 ? 2 : 1));
        int b = normalize(sampleColor.getBlue() + rgb[2] * factor * (mod == 2 ? 2 : 1));
        float[] hsbNew = Color.RGBtoHSB(r, g, b, null);
        float[] hsbOrig = Color.RGBtoHSB(sampleColor.getRed(), sampleColor.getGreen(), sampleColor.getBlue(), null);

        //System.out.println("#" + nestLevel + ":" + sampleColor + " diff:" + circle.second + " conflict:" + circle.first + " now:" +Color.getHSBColor(hsbNew[0], hsbNew[1], hsbOrig[2]));
        return resolveConflict(colorCircles,
                               Color.getHSBColor(hsbNew[0], hsbNew[1], (hsbOrig[2] + hsbNew[2])/2), 
                               ++nestLevel);
      }
    }
    return sampleColor;
  }

  private static int normalize(double b) {
    return MathUtil.clamp((int)b, 1, 254);
  }

  public static double colorDistance01(@NotNull Color c1, @NotNull Color c2) {
    return getLength(YPbPr01(rgbDiffColor(c1, c2)));
  }

  private static double getLength(float @NotNull [] components) {
    return Math.sqrt(components[0] * components[0] + components[1] * components[1] + components[2] * components[2]);
  }

  private static float @NotNull [] rgbDiffColor(@NotNull Color c1, @NotNull Color c2) {
    return new float[] {
      c1.getRed() - c2.getRed(),
      c1.getGreen() - c2.getGreen(),
      c1.getBlue() - c2.getBlue()
    };
  }

  @Contract(pure = true)
  private static float @NotNull [] YPbPr01(float @NotNull [] rgb) {
    // http://www.equasys.de/colorconversion.html
    return new float[]{
      (float)((  0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2])/256),
      (float)(( -0.169 * rgb[0] - 0.331 * rgb[1] + 0.500 * rgb[2])/256),
      (float)((  0.500 * rgb[0] - 0.419 * rgb[1] - 0.081 * rgb[2])/256)
    };
  }

  private static Color @Nullable [] getColorsFromCache(@NotNull TextAttributesScheme colorsScheme) {
    List<Color> colors = new ArrayList<>();
    boolean validCache = true;
    for (TextAttributesKey tempKey : RAINBOW_TEMP_KEYS) {
      TextAttributes attributes = colorsScheme.getAttributes(tempKey);
      if (attributes == null) {
        validCache = false;
        break;
      }
      colors.add(getRainbowColorFromAttribute(attributes));
    }
    if (!validCache) {
      return null;
    }
    return colors.toArray(new Color[0]);
  }

  private static @Nullable Color getRainbowColorFromAttribute(@NotNull TextAttributes attributes) {
    return attributes.getForegroundColor();
  }

  private static void setRainbowColorToAttribute(@NotNull TextAttributes attributes, @Nullable Color rainbowColor) {
    attributes.setForegroundColor(rainbowColor);
  }

  public static TextAttributesKey @NotNull [] getRainbowTempKeys(@NotNull TextAttributesScheme colorsScheme) {
    generateColors(colorsScheme);
    return RAINBOW_TEMP_KEYS;
  }

  @Contract(pure = true)
  public static @NotNull @UnmodifiableView Map<String, TextAttributesKey> getRainbowRegereratedKeyMap() {
    return Collections.unmodifiableMap(RAINBOW_TEMP_TAG_TO_KEY_MAP);
  }

  public static void createLazyRainbowKeyIfNeed(@Nullable TextAttributesKey maybeRegeneratedRainbowKey, @NotNull EditorColorsScheme scheme) {
    if (maybeRegeneratedRainbowKey != null
        && getRainbowRegereratedKeyMap().containsKey(maybeRegeneratedRainbowKey.getExternalName())
        && scheme.getAttributes(maybeRegeneratedRainbowKey) == null) {
      resetRainbowGeneratedColors(scheme);
    }
  }

  public static boolean isRainbowTempKey(@NotNull TextAttributesKey key) {
    return key.getExternalName().startsWith(RAINBOW_TEMP_PREF);
  }

  public HighlightInfo getInfo(int colorIndex, @Nullable PsiElement id, @Nullable TextAttributesKey colorKey) {
    return id == null ? null : getInfoBuilder(colorIndex, colorKey).range(id).create();
  }

  public HighlightInfo getInfo(int colorIndex, int start, int end, @Nullable TextAttributesKey colorKey) {
    return getInfoBuilder(colorIndex, colorKey).range(start, end).create();
  }

  @SuppressWarnings("unused")
  private @NotNull HighlightInfo.Builder getInfoBuilder(int colorIndex, @Nullable TextAttributesKey originalEntityTagKey) {
    return HighlightInfo
      .newHighlightInfo(RAINBOW_ELEMENT)
      .textAttributes(getRainbowAttrWithLazyCreation(myColorsScheme, colorIndex));

    // Bad-for-remote IDE approach: no fixed tag to repaint in the light client
    //if (originalEntityTagKey == null) {
    //  originalEntityTagKey = DefaultLanguageHighlighterColors.LOCAL_VARIABLE;
    //}
    //return HighlightInfo
    //  .newHighlightInfo(RAINBOW_ELEMENT)
    //  .textAttributes(TextAttributes
    //                  .fromFlyweight(myColorsScheme
    //                                   .getAttributes(originalEntityTagKey)
    //                                   .getFlyweight()
    //                                   .withForeground(myRainbowColors[colorIndex])));
  }

  public static @NotNull TextAttributes createRainbowAttribute(@Nullable Color color) {
    TextAttributes ret = new TextAttributes();
    setRainbowColorToAttribute(ret, color);
    return ret;
  }

  public static @NotNull Map<String, TextAttributesKey> createRainbowHLM() {
    Map<String, TextAttributesKey> hashMap = new HashMap<>();
    hashMap.put(RAINBOW_ANCHOR.getExternalName(), RAINBOW_ANCHOR);
    hashMap.put(RAINBOW_GRADIENT_DEMO.getExternalName(), RAINBOW_GRADIENT_DEMO);
    hashMap.putAll(RAINBOW_TEMP_TAG_TO_KEY_MAP);
    return hashMap;
  }
}
