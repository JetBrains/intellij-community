// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ColoredOutputTypeRegistryImpl extends ColoredOutputTypeRegistry {
  private final Map<String, ProcessOutputType> myStdoutAttrsToKeyMap = new ConcurrentHashMap<>();
  private final Map<String, ProcessOutputType> myStderrAttrsToKeyMap = new ConcurrentHashMap<>();

  private static final TextAttributesKey[] myAnsiColorKeys = new TextAttributesKey[]{
    ConsoleHighlighter.BLACK,
    ConsoleHighlighter.RED,
    ConsoleHighlighter.GREEN,
    ConsoleHighlighter.YELLOW,
    ConsoleHighlighter.BLUE,
    ConsoleHighlighter.MAGENTA,
    ConsoleHighlighter.CYAN,
    ConsoleHighlighter.GRAY,

    ConsoleHighlighter.DARKGRAY,
    ConsoleHighlighter.RED_BRIGHT,
    ConsoleHighlighter.GREEN_BRIGHT,
    ConsoleHighlighter.YELLOW_BRIGHT,
    ConsoleHighlighter.BLUE_BRIGHT,
    ConsoleHighlighter.MAGENTA_BRIGHT,
    ConsoleHighlighter.CYAN_BRIGHT,
    ConsoleHighlighter.WHITE,
  };

  /*
    Description
     0	Cancel all attributes
     1	Bright (bold)
     2	Normal (not bold)
     3	Italic
     4	Underline
     5	Blink
     7	Reverse video
     8	Concealed (don't display characters)
     9	Strike through
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
  @Override
  public @NotNull ProcessOutputType getOutputType(@NonNls String attribute, @NotNull Key streamType) {
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
    ConsoleViewContentType.registerNewConsoleViewType(newKey, createAnsiConsoleViewContentType(attribute));
    attrsToKeyMap.put(completeAttribute, newKey);
    return newKey;
  }

  /**
   * Creates an {@link ProcessOutputType} from the {@link AnsiTerminalEmulator terminal emulator state} and stream type. Output type may be used
   * later to print to the console
   */
  @Override
  public @NotNull ProcessOutputType getOutputType(@NotNull AnsiTerminalEmulator terminal, @NotNull Key streamType) {
    Map<String, ProcessOutputType> attrsToKeyMap = ProcessOutputType.isStdout(streamType) ? myStdoutAttrsToKeyMap : myStderrAttrsToKeyMap;
    String ansiSerializedState = terminal.getAnsiSerializedSGRState();
    ProcessOutputType key = attrsToKeyMap.get(ansiSerializedState);
    if (key != null) {
      return key;
    }

    ProcessOutputType streamOutputType = streamType instanceof ProcessOutputType ?
                                         (ProcessOutputType)streamType : (ProcessOutputType)ProcessOutputTypes.STDOUT;

    ProcessOutputType newKey = new ProcessOutputType(ansiSerializedState, streamOutputType);
    ConsoleViewContentType.registerNewConsoleViewType(newKey, createAnsiConsoleViewContentType(terminal));
    attrsToKeyMap.put(ansiSerializedState, newKey);
    return newKey;
  }

  private static Color getAnsiColor(final int value) {
    return getColorByKey(getAnsiColorKey(value));
  }

  private static Color getColorByKey(TextAttributesKey colorKey) {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(colorKey).getForegroundColor();
  }

  private static @NotNull Color getDefaultForegroundColor() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attr = scheme.getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY);
    Color color = attr != null ? attr.getForegroundColor() : null;
    if (color == null) {
      color = scheme.getDefaultForeground();
    }
    return color;
  }

  private static @NotNull Color getDefaultBackgroundColor() {
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
    return myAnsiColorKeys[value];
  }

  private static @NotNull ConsoleViewContentType createAnsiConsoleViewContentType(@NotNull String attribute) {
    int foregroundColor = -1;
    int backgroundColor = -1;
    boolean inverse = false;
    EffectType effectType = null;
    int fontType = -1;
    final String[] strings = attribute.split(";");
    for (String string : strings) {
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
        //TODO: 256 colors foreground
      }
      else if (value == 39) {
        foregroundColor = -1;
      }
      else if (value >= 40 && value <= 47) {
        backgroundColor = value - 40;
      }
      else if (value == 48) {
        //TODO: 256 colors background
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
    return createAnsiConsoleViewContentType(
      attribute,
      backgroundColor,
      foregroundColor,
      null,
      null,
      inverse,
      effectType == null ? Collections.emptyList() : Collections.singletonList(effectType),
      fontType
    );
  }

  private static ConsoleViewContentType createAnsiConsoleViewContentType(@NotNull AnsiTerminalEmulator terminalEmulator) {
    return createAnsiConsoleViewContentType(
      terminalEmulator.getAnsiSerializedSGRState(),
      terminalEmulator.getBackgroundColorIndex(),
      terminalEmulator.getForegroundColorIndex(),
      terminalEmulator.getBackgroundColor(),
      terminalEmulator.getForegroundColor(),
      terminalEmulator.isInverse(),
      computeEffectTypes(terminalEmulator),
      computeAwtFont(terminalEmulator)
    );
  }

  private static @NotNull List<EffectType> computeEffectTypes(@NotNull AnsiTerminalEmulator terminalEmulator) {
    ArrayList<EffectType> result = new ArrayList<>();
    AnsiTerminalEmulator.Underline underline = terminalEmulator.getUnderline();
    if (underline == AnsiTerminalEmulator.Underline.SINGLE_UNDERLINE) {
      result.add(EffectType.LINE_UNDERSCORE);
    }
    else if (underline == AnsiTerminalEmulator.Underline.DOUBLE_UNDERLINE) {
      result.add(EffectType.BOLD_LINE_UNDERSCORE);
    }
    if (terminalEmulator.isCrossedOut()) {
      result.add(EffectType.STRIKEOUT);
    }
    AnsiTerminalEmulator.FrameType frameType = terminalEmulator.getFrameType();
    if (frameType == AnsiTerminalEmulator.FrameType.FRAMED) {
      result.add(EffectType.BOXED);
    }
    else if (frameType == AnsiTerminalEmulator.FrameType.ENCIRCLED) {
      result.add(EffectType.ROUNDED_BOX);
    }
    return result;
  }

  private static int computeAwtFont(@NotNull AnsiTerminalEmulator terminalEmulator) {
    int result = 0;
    if (terminalEmulator.getWeight() == AnsiTerminalEmulator.Weight.BOLD) {
      result = Font.BOLD;
    }
    if (terminalEmulator.isItalic()) {
      result |= Font.ITALIC;
    }
    return result;
  }

  private static ConsoleViewContentType createAnsiConsoleViewContentType(
    @NotNull String attribute,
    int backgroundColorIndex,
    int foregroundColorIndex,
    @Nullable Color enforcedBackgroundColor,
    @Nullable Color enforcedForegroundColor,
    boolean inverse,
    @NotNull java.util.List<EffectType> effectTypes,
    int fontType
  ) {
    TextAttributes attrs = new TextAttributes();
    attrs.setEffectType(null); // re-setting default BOX
    if (fontType != -1) {
      attrs.setFontType(fontType);
    }

    Color foregroundColor = getColor(foregroundColorIndex, enforcedForegroundColor, ColoredOutputTypeRegistryImpl::getDefaultForegroundColor);
    Color backgroundColor = getColor(backgroundColorIndex, enforcedBackgroundColor, ColoredOutputTypeRegistryImpl::getDefaultBackgroundColor);
    if (inverse) {
      attrs.setForegroundColor(backgroundColor);
      attrs.setEffectColor(backgroundColor);
      attrs.setBackgroundColor(foregroundColor);
      effectTypes.forEach(it -> attrs.withAdditionalEffect(it, backgroundColor));
    }
    else {
      attrs.setForegroundColor(foregroundColor);
      attrs.setEffectColor(foregroundColor);
      attrs.setBackgroundColor(backgroundColor);
      effectTypes.forEach(it -> attrs.withAdditionalEffect(it, foregroundColor));
    }

    return new ConsoleViewContentType(attribute, attrs);
  }

  private static Color getColor(int colorIndex, Color enforcedColor, Supplier<Color> getDefaultColor) {
    if (enforcedColor != null) {
      return enforcedColor;
    }

    return colorIndex != -1 ? getAnsiColor(colorIndex) : getDefaultColor.get();
  }
}
