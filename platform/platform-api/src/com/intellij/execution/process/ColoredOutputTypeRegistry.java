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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class ColoredOutputTypeRegistry {
  public static ColoredOutputTypeRegistry getInstance() {
    return ServiceManager.getService(ColoredOutputTypeRegistry.class);
  }

  private final Map<String, Key> myRegisteredKeys = new HashMap<>();

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
  public Key getOutputKey(@NonNls String attribute) {
    final Key key = myRegisteredKeys.get(attribute);
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
      return ProcessOutputTypes.STDOUT;
    }
    TextAttributes attrs = new AnsiTextAttributes(attribute);
    Key newKey = new Key(completeAttribute);
    ConsoleViewContentType contentType = new ConsoleViewContentType(completeAttribute, attrs);
    ConsoleViewContentType.registerNewConsoleViewType(newKey, contentType);
    myRegisteredKeys.put(completeAttribute, newKey);
    return newKey;
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
    return myAnsiColorKeys[value];
  }

  private static class AnsiTextAttributes extends TextAttributes {
    int myForegroundColor = -1;
    int myBackgroundColor = -1;
    boolean myInverse = false;

    public AnsiTextAttributes(String attribute) {
      setEffectType(null);
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
          setFontType(Font.BOLD);
        }
        else if (value == 4) {
          setEffectType(EffectType.LINE_UNDERSCORE);
        }
        else if (value == 7) {
          myInverse = true;
        }
        else if (value == 22) {
          setFontType(Font.PLAIN);
        }
        else if (value == 24) {  //not underlined
          setEffectType(null);
        }
        else if (value >= 30 && value <= 37) {
          myForegroundColor = value - 30;
        }
        else if (value == 38) {
          //TODO: 256 colors foreground
        }
        else if (value == 39) {
          myForegroundColor = -1;
        }
        else if (value >= 40 && value <= 47) {
          myBackgroundColor = value - 40;
        }
        else if (value == 48) {
          //TODO: 256 colors background
        }
        else if (value == 49) {
          myBackgroundColor = -1;
        }
        else if (value >= 90 && value <= 97) {
          myForegroundColor = value - 82;
        }
        else if (value >= 100 && value <= 107) {
          myBackgroundColor = value - 92;
        }
      }
    }

    @Override
    public Color getForegroundColor() {
      if (myInverse) return myBackgroundColor < 0 ? getDefaultBackgroundColor() : getAnsiColor(myBackgroundColor);
      return myForegroundColor < 0 ? getDefaultForegroundColor() : getAnsiColor(myForegroundColor);
    }

    @Override
    public Color getBackgroundColor() {
      if (myInverse) return myForegroundColor < 0 ? getDefaultForegroundColor() : getAnsiColor(myForegroundColor);
      return myBackgroundColor < 0 ? getDefaultBackgroundColor() : getAnsiColor(myBackgroundColor);
    }

    @Override
    public Color getEffectColor() {
      return getEffectType() != null ? getForegroundColor() : null;
    }
  }
}
