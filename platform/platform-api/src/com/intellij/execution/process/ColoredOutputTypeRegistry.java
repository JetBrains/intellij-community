/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.stream.IntStream;

/**
 * @author yole
 */
public class ColoredOutputTypeRegistry {
  public static ColoredOutputTypeRegistry getInstance() {
    return ServiceManager.getService(ColoredOutputTypeRegistry.class);
  }

  private final Map<String, ProcessOutputType> myStdoutAttrsToKeyMap = ContainerUtil.newConcurrentMap();
  private final Map<String, ProcessOutputType> myStderrAttrsToKeyMap = ContainerUtil.newConcurrentMap();

  private static class AnsiColorInfo {
    private Color myOriginalColor;
    private TextAttributesKey myColorKey;

    private AnsiColorInfo(Color originalColor, TextAttributesKey colorKey) {
      myOriginalColor = originalColor;
      myColorKey = colorKey;
    }

    private Color getOriginalColor() {
      return myOriginalColor;
    }

    private TextAttributesKey getColorKey() {
      return myColorKey;
    }
  }

  private static final AnsiColorInfo[] myAnsiColorInfos = new AnsiColorInfo[]{
    new AnsiColorInfo(new Color(0, 0, 0), ConsoleHighlighter.BLACK),
    new AnsiColorInfo(new Color(128, 0, 0), ConsoleHighlighter.RED),
    new AnsiColorInfo(new Color(0, 128, 0), ConsoleHighlighter.GREEN),
    new AnsiColorInfo(new Color(128, 128, 0), ConsoleHighlighter.YELLOW),
    new AnsiColorInfo(new Color(0, 0, 128), ConsoleHighlighter.BLUE),
    new AnsiColorInfo(new Color(128, 0, 128), ConsoleHighlighter.MAGENTA),
    new AnsiColorInfo(new Color(0, 128, 128), ConsoleHighlighter.CYAN),
    new AnsiColorInfo(new Color(192, 192, 192), ConsoleHighlighter.GRAY),

    new AnsiColorInfo(new Color(128, 128, 128), ConsoleHighlighter.DARKGRAY),
    new AnsiColorInfo(new Color(255, 0, 0), ConsoleHighlighter.RED_BRIGHT),
    new AnsiColorInfo(new Color(0, 255, 0), ConsoleHighlighter.GREEN_BRIGHT),
    new AnsiColorInfo(new Color(255, 255, 0), ConsoleHighlighter.YELLOW_BRIGHT),
    new AnsiColorInfo(new Color(0, 0, 255), ConsoleHighlighter.BLUE_BRIGHT),
    new AnsiColorInfo(new Color(255, 0, 255), ConsoleHighlighter.MAGENTA_BRIGHT),
    new AnsiColorInfo(new Color(0, 255, 255), ConsoleHighlighter.CYAN_BRIGHT),
    new AnsiColorInfo(new Color(255, 255, 255), ConsoleHighlighter.WHITE)
  };

  /*
    Description
     0	Cancel all attributes except foreground/background color
     1	Bright (bold)
     2	Normal (not bold)
     4	Underline
     5	Blink
     7	Reverse video
     8	Concealed (don't display characters)
     30	Make foreground (the characters) black
     31	Make foreground red
     32	Make foreground green
     33	Make foreground yellow
     34	Make foreground blue
     35	Make foreground magenta
     36	Make foreground cyan
     37	Make foreground white

     40	Make background (around the characters) black
     41	Make background red
     42	Make background green
     43	Make background yellow
     44	Make background blue
     45	Make background magenta
     46	Make background cyan
     47	Make background white (you may need 0 instead, or in addition)

     see full doc at http://en.wikipedia.org/wiki/ANSI_escape_code
  */
  @NotNull
  public ProcessOutputType getOutputType(@NonNls String attribute, @NotNull Key streamType) {
    ProcessOutputType streamOutputType = streamType instanceof ProcessOutputType ? (ProcessOutputType)streamType
                                                                                 : (ProcessOutputType)ProcessOutputTypes.STDOUT;
    Map<String, ProcessOutputType> attrsToKeyMap = ProcessOutputType.isStdout(streamType) ? myStdoutAttrsToKeyMap : myStderrAttrsToKeyMap;
    ProcessOutputType key = attrsToKeyMap.get(attribute);
    if (key != null) {
      return key;
    }
    final String completeAttribute = attribute;
    if (attribute.startsWith("\u001B[")) {
      attribute = attribute.substring(2);
    }
    else {
      attribute = StringUtil.trimStart(attribute, "[");
    }
    attribute = StringUtil.trimEnd(attribute, "m");
    if (attribute.equals("0")) {
      return streamOutputType;
    }
    ProcessOutputType newKey = new ProcessOutputType(completeAttribute, streamOutputType);
    AnsiConsoleViewContentType contentType = createAnsiConsoleViewContentType(attribute);
    ConsoleViewContentType.registerNewConsoleViewType(newKey, contentType);
    attrsToKeyMap.put(completeAttribute, newKey);
    return newKey;
  }

  /**
   * @deprecated use {@link #getOutputType(String, Key)} instead
   */
  @Deprecated
  @NotNull
  public Key getOutputKey(@NonNls String attribute) {
    return getOutputType(attribute, ProcessOutputTypes.STDOUT);
  }

  private static Color getAnsiColor(final int value) {
    return getColorByKey(getAnsiColorKey(value));
  }

  private static Color getColorByKey(TextAttributesKey colorKey) {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(colorKey).getForegroundColor();
  }

  @NotNull
  private static Color getDefaultForegroundColor() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attr = scheme.getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY);
    Color color = attr != null ? attr.getForegroundColor() : null;
    if (color == null) {
      color = scheme.getDefaultForeground();
    }
    return color;
  }

  @NotNull
  private static Color getDefaultBackgroundColor() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Color color = scheme.getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY);
    if (color == null) {
      color = scheme.getDefaultBackground();
    }
    return color;
  }

  public static TextAttributesKey getAnsiColorKey(int value) {
    if (value >= 16) {
      return ConsoleViewContentType.NORMAL_OUTPUT_KEY;
    }
    return myAnsiColorInfos[value].getColorKey();
  }

  private static Color parseColor(@NotNull Iterator<String> strings) {
    int formatCode = Integer.parseInt(strings.next());

    if (formatCode == 2) { // 24-bit color format
      int red = Integer.parseInt(strings.next());
      int green = Integer.parseInt(strings.next());
      int blue = Integer.parseInt(strings.next());
      return new Color(red, green, blue);
    }

    if (formatCode == 5) { // 8-bit color format
      int value = Integer.parseInt(strings.next());

      // Standard colors or high intensity colors
      if (value >= 0 && value < 16) {
        return myAnsiColorInfos[value].getOriginalColor();
      }

      // 6 × 6 × 6 cube (216 colors): 16 + 36 × r + 6 × g + b (0 ≤ r, g, b ≤ 5)
      if (value >= 16 && value < 232) {
        int red = (value - 16) / 36 * 51;
        int green = (value - 16) % 36 / 6 * 51;
        int blue = (value - 16) % 6 * 51;
        return new Color(red, green, blue);
      }

      // Grayscale from black to white in 24 steps
      if (value >= 232 && value < 256) {
        int grayscale = (value - 232) * 10 + 81;
        return new Color(grayscale, grayscale, grayscale);
      }
    }

    throw new IllegalArgumentException("Invalid color format: " + formatCode);
  }

  private static int getNearestColorAttribute(Color color) {
    return IntStream.range(0, myAnsiColorInfos.length)
      .boxed()
      .min(Comparator.comparingDouble(i -> getHypotForColors(myAnsiColorInfos[i].getOriginalColor(), color)))
      .orElse(-1);
  }

  private static double getHypotForColors(Color from, Color to) {
    int dRed = from.getRed() - to.getRed();
    int dGreen = from.getGreen() - to.getGreen();
    int dBlue = from.getBlue() - to.getBlue();
    return Math.sqrt(dRed * dRed + dGreen * dGreen + dBlue * dBlue);
  }

  @NotNull
  private static AnsiConsoleViewContentType createAnsiConsoleViewContentType(@NotNull String attribute) {
    int foregroundColor = -1;
    int backgroundColor = -1;
    boolean inverse = false;
    EffectType effectType = null;
    int fontType = -1;
    final Iterator<String> strings = ContainerUtil.iterate(attribute.split(";"));
    while (strings.hasNext()) {
      String string = strings.next();
      int value;
      try {
        value = Integer.parseInt(string);
      }
      catch (NumberFormatException e) {
        continue;
      }
      if (value == 1) {
        fontType = Font.BOLD;
      }
      else if (value == 4) {
        effectType = EffectType.LINE_UNDERSCORE;
      }
      else if (value == 7) {
        inverse = true;
      }
      else if (value == 22) {
        fontType = Font.PLAIN;
      }
      else if (value == 24) {  //not underlined
        effectType = null;
      }
      else if (value >= 30 && value <= 37) {
        foregroundColor = value - 30;
      }
      else if (value == 38) {
        try {
          foregroundColor = getNearestColorAttribute(parseColor(strings));
        }
        catch (IllegalArgumentException | NoSuchElementException e) {
          // continue;
        }
      }
      else if (value == 39) {
        foregroundColor = -1;
      }
      else if (value >= 40 && value <= 47) {
        backgroundColor = value - 40;
      }
      else if (value == 48) {
        try {
          backgroundColor = getNearestColorAttribute(parseColor(strings));
        }
        catch (IllegalArgumentException | NoSuchElementException e) {
          // continue;
        }
      }
      else if (value == 49) {
        backgroundColor = -1;
      }
      else if (value >= 90 && value <= 97) {
        foregroundColor = value - 82;
      }
      else if (value >= 100 && value <= 107) {
        backgroundColor = value - 92;
      }
    }
    return new AnsiConsoleViewContentType(attribute, backgroundColor, foregroundColor, inverse, effectType, fontType);
  }

  private static class AnsiConsoleViewContentType extends ConsoleViewContentType {
    private final int myBackgroundColor;
    private final int myForegroundColor;
    private final boolean myInverse;
    private final EffectType myEffectType;
    private final int myFontType;

    private AnsiConsoleViewContentType(@NotNull String attribute,
                                       int backgroundColor,
                                       int foregroundColor,
                                       boolean inverse,
                                       @Nullable EffectType effectType,
                                       int fontType) {
      super(attribute, ConsoleViewContentType.NORMAL_OUTPUT_KEY);
      myBackgroundColor = backgroundColor;
      myForegroundColor = foregroundColor;
      myInverse = inverse;
      myEffectType = effectType;
      myFontType = fontType;
    }

    @Override
    public TextAttributes getAttributes() {
      TextAttributes attrs = new TextAttributes();
      attrs.setEffectType(myEffectType);
      if (myFontType != -1) {
        attrs.setFontType(myFontType);
      }
      Color foregroundColor = getForegroundColor();
      Color backgroundColor = getBackgroundColor();
      if (myInverse) {
        attrs.setForegroundColor(backgroundColor);
        attrs.setEffectColor(backgroundColor);
        attrs.setBackgroundColor(foregroundColor);
      }
      else {
        attrs.setForegroundColor(foregroundColor);
        attrs.setEffectColor(foregroundColor);
        attrs.setBackgroundColor(backgroundColor);
      }
      return attrs;
    }

    private Color getForegroundColor() {
      return myForegroundColor != -1 ? getAnsiColor(myForegroundColor) : getDefaultForegroundColor();
    }

    private Color getBackgroundColor() {
      return myBackgroundColor != -1 ? getAnsiColor(myBackgroundColor) : getDefaultBackgroundColor();
    }
  }
}
