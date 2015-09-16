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

public enum AntialiasingType {
  SUBPIXEL,
  GREYSCALE,
  OFF;

  private static final SwingUtilities2.AATextInfo aaEnabled =
    new SwingUtilities2.AATextInfo(RenderingHints.VALUE_TEXT_ANTIALIAS_ON, 140);

  private static final SwingUtilities2.AATextInfo lcdEnabled =
    new SwingUtilities2.AATextInfo(RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, 140);

  private static final SwingUtilities2.AATextInfo aaDisabled = null;

  public static Object getAAHintForSwingComponent() {
    UISettings uiSettings = UISettings.getInstance();

    if (uiSettings == null) return aaEnabled;

    switch (uiSettings.IDE_AA_TYPE) {
      case SUBPIXEL:
        return lcdEnabled;
      case GREYSCALE:
        return aaEnabled;
      case OFF:
        return aaDisabled;
    }

    return aaEnabled;
  }

  public Object getRenderingHintValue () {
    Object value = RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB;
    switch (this) {
      case SUBPIXEL:
        break;
      case GREYSCALE:
        value = RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
        break;
      case OFF:
        value = RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
        break;
    }
    return value;
  }

  public static Object getKeyForCurrentScope(boolean inEditor) {
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings == null) return RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
    return inEditor ? uiSettings.EDITOR_AA_TYPE.getRenderingHintValue() : uiSettings.IDE_AA_TYPE.getRenderingHintValue();
  }

  @Override
  public String toString() {
    String description ;
    switch (this) {
      case SUBPIXEL:
        description = "Subpixel";
        break;
      case GREYSCALE:
        description = "Greyscale";
        break;
      case OFF:
        description = "No antialiasing";
        break;
      default:
        description = "Subpixel";
    }
    return description;
  }
 }
