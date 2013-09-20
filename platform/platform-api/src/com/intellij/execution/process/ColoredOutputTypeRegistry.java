package com.intellij.execution.process;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;

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

  private final Map<String, Key> myRegisteredKeys = new HashMap<String, Key>();

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

  public Key getOutputKey(@NonNls String attribute) {
    final Key key = myRegisteredKeys.get(attribute);
    if (key != null) {
      return key;
    }
    final String completeAttribute = attribute;
    if (attribute.startsWith("\u001B[")) {
      attribute = attribute.substring(2);
    }
    else if (attribute.startsWith("[")) {
      attribute = attribute.substring(1);
    }
    if (attribute.endsWith("m")) {
      attribute = attribute.substring(0, attribute.length() - 1);
    }
    if (attribute.equals("0")) {
      return ProcessOutputTypes.STDOUT;
    }
    TextAttributes attrs = new TextAttributes();
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
        attrs.setFontType(Font.BOLD);
      }
      else if (value == 4) {
        attrs.setEffectType(EffectType.LINE_UNDERSCORE);
      }
      else if (value == 22) {
        attrs.setFontType(Font.PLAIN);
      }
      else if (value == 24) {  //not underlined
        attrs.setEffectType(null);
      }
      else if (value >= 30 && value <= 37) {
        attrs.setForegroundColor(getAnsiColor(value - 30));
      }
      else if (value == 38) {
        //TODO: 256 colors foreground
      }
      else if (value == 39) {
        attrs.setForegroundColor(getColorByKey(ConsoleViewContentType.NORMAL_OUTPUT_KEY));
      }
      else if (value >= 40 && value <= 47) {
        attrs.setBackgroundColor(getAnsiColor(value - 40));
      }
      else if (value == 48) {
        //TODO: 256 colors background
      }
      else if (value == 49) {
        attrs.setBackgroundColor(getColorByKey(ConsoleViewContentType.NORMAL_OUTPUT_KEY));
      }
      else if (value >= 90 && value <= 97) {
        attrs.setForegroundColor(
          getAnsiColor(value - 82));
      }
      else if (value >= 100 && value <= 107) {
        attrs.setBackgroundColor(
          getAnsiColor(value - 92));
      }
    }
    if (attrs.getEffectType() == EffectType.LINE_UNDERSCORE) {
      attrs.setEffectColor(attrs.getForegroundColor());
    }
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

  public static TextAttributesKey getAnsiColorKey(int value) {
    if (value >= 16) {
      return ConsoleViewContentType.NORMAL_OUTPUT_KEY;
    }
    return myAnsiColorKeys[value];
  }
}
