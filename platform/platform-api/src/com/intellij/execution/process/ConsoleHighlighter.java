/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.PlatformColors;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author oleg
 */
public class ConsoleHighlighter {
  @NonNls static final String RED_ID = "CONSOLE_RED_OUTPUT";
  @NonNls static final String GREEN_ID = "CONSOLE_GREEN_OUTPUT";
  @NonNls static final String YELLOW_ID = "CONSOLE_YELLOW_OUTPUT";
  @NonNls static final String BLUE_ID = "CONSOLE_BLUE_OUTPUT";
  @NonNls static final String MAGENTA_ID = "CONSOLE_MAGENTA_OUTPUT";
  @NonNls static final String CYAN_ID = "CONSOLE_CYAN_OUTPUT";
  @NonNls static final String GRAY_ID = "CONSOLE_GRAY_OUTPUT";

  public static final TextAttributes RED_DEFAULT_ATTRS = HighlighterColors.TEXT.getDefaultAttributes().clone();
  public static final TextAttributes GREEN_DEFAULT_ATTRS = HighlighterColors.TEXT.getDefaultAttributes().clone();
  public static final TextAttributes YELLOW_DEFAULT_ATTRS = HighlighterColors.TEXT.getDefaultAttributes().clone();
  public static final TextAttributes BLUE_DEFAULT_ATTRS = HighlighterColors.TEXT.getDefaultAttributes().clone();
  public static final TextAttributes MAGENTA_DEFAULT_ATTRS = HighlighterColors.TEXT.getDefaultAttributes().clone();
  public static final TextAttributes CYAN_DEFAULT_ATTRS = HighlighterColors.TEXT.getDefaultAttributes().clone();
  public static final TextAttributes GRAY_DEFAULT_ATTRS = HighlighterColors.TEXT.getDefaultAttributes().clone();

  static {
    RED_DEFAULT_ATTRS.setForegroundColor(JBColor.RED);
    GREEN_DEFAULT_ATTRS.setForegroundColor(new Color(0, 128, 0));
    YELLOW_DEFAULT_ATTRS.setForegroundColor(new Color(255, 204, 0));
    BLUE_DEFAULT_ATTRS.setForegroundColor(PlatformColors.BLUE);
    MAGENTA_DEFAULT_ATTRS.setForegroundColor(JBColor.MAGENTA);
    CYAN_DEFAULT_ATTRS.setForegroundColor(JBColor.CYAN.darker());
    GRAY_DEFAULT_ATTRS.setForegroundColor(JBColor.GRAY.darker());
  }

  public static final TextAttributesKey RED = TextAttributesKey.createTextAttributesKey(RED_ID, RED_DEFAULT_ATTRS);
  public static final TextAttributesKey GREEN = TextAttributesKey.createTextAttributesKey(GREEN_ID, GREEN_DEFAULT_ATTRS);
  public static final TextAttributesKey YELLOW = TextAttributesKey.createTextAttributesKey(YELLOW_ID, YELLOW_DEFAULT_ATTRS);
  public static final TextAttributesKey BLUE = TextAttributesKey.createTextAttributesKey(BLUE_ID, BLUE_DEFAULT_ATTRS);
  public static final TextAttributesKey MAGENTA = TextAttributesKey.createTextAttributesKey(MAGENTA_ID, MAGENTA_DEFAULT_ATTRS);
  public static final TextAttributesKey CYAN = TextAttributesKey.createTextAttributesKey(CYAN_ID, CYAN_DEFAULT_ATTRS);
  public static final TextAttributesKey GRAY = TextAttributesKey.createTextAttributesKey(GRAY_ID, GRAY_DEFAULT_ATTRS);

  private ConsoleHighlighter() {
  }
}