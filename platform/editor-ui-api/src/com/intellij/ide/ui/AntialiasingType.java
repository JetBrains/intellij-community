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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import java.awt.*;

public enum AntialiasingType {
  SUBPIXEL,
  GREYSCALE,
  OFF;

  private static final SwingUtilities2.AATextInfo aaDisabled = null;

  private static SwingUtilities2.AATextInfo getLCDEnabledTextInfo () {
    return new SwingUtilities2.AATextInfo(RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, UIUtil.getLcdContrastValue());
  }

  private static SwingUtilities2.AATextInfo getAAEnabledTextInfo () {
    return new SwingUtilities2.AATextInfo(RenderingHints.VALUE_TEXT_ANTIALIAS_ON, UIUtil.getLcdContrastValue());
  }

  public static Object getAAHintForSwingComponent() {
    if (ApplicationManager.getApplication() == null) {
      return getAAEnabledTextInfo();
    }

    UISettings uiSettings = UISettings.getInstance();

    switch (uiSettings.IDE_AA_TYPE) {
      case SUBPIXEL:
        return getLCDEnabledTextInfo();
      case GREYSCALE:
        return getAAEnabledTextInfo();
      case OFF:
        return aaDisabled;
    }

    return getAAEnabledTextInfo();
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
