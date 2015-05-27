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
package com.intellij.ide.ui;

import sun.swing.SwingUtilities2;
import java.awt.*;

public enum LCDRenderingScope {
  IDE,
  EXCLUDING_EDITOR,
  OFF;

  public static LCDRenderingScope getWithLongestName() {
    return IDE;
  }

  private static final SwingUtilities2.AATextInfo aaEnabled =
    new SwingUtilities2.AATextInfo(RenderingHints.VALUE_TEXT_ANTIALIAS_ON, 140);

  private static final SwingUtilities2.AATextInfo lcdEnabled =
    new SwingUtilities2.AATextInfo(RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, 140);

  private static final SwingUtilities2.AATextInfo aaDisabled = null;

  public static Object getAAHintForSwingComponent() {
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.ANTIALIASING_IN_IDE) {
      if (uiSettings.LCD_RENDERING_SCOPE == OFF) {
        return aaEnabled;
      }
      return lcdEnabled;
    }
    return aaDisabled;
  }


  public static Object getKeyForCurrentScope(boolean inEditor) {
    Object renderingHint = RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;

    UISettings uiSettings = UISettings.getInstance();

    if (uiSettings != null && uiSettings.ANTIALIASING_IN_IDE) {
      switch (uiSettings.LCD_RENDERING_SCOPE) {
        case IDE:
          renderingHint = RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB;
          break;
        case EXCLUDING_EDITOR:
          if (!inEditor) {
            renderingHint = RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB;
            break;
          }
        case OFF:
          renderingHint = RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
          break;
      }
    }
    return renderingHint;
  }

  @Override
  public String toString() {
    String description ;
    switch (this) {
      case IDE:
        description = "LCD Rendering in IDE and Editor";
        break;
      case EXCLUDING_EDITOR:
        description = "LCD Rendering in IDE";
        break;
      case OFF:
        description = "Without LCD rendering";
        break;
      default:
        description = "LCD Rendering in IDE";
    }
    return description;
  }

  public static boolean shouldRenderEditor(LCDRenderingScope scope) {
    if (!UISettings.getInstance().ANTIALIASING_IN_IDE) return false;
    switch (scope) {
      case IDE:
        return true;
      default:
        return false;
    }
  }
}
