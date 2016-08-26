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
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ui.FontInfo;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicLookAndFeel;
import javax.swing.plaf.metal.DefaultMetalTheme;
import java.awt.*;
import java.util.HashSet;

/**
 * @author Konstantin Bulenkov
 */
public class IntelliJLaf extends DarculaLaf {
  @Override
  public String getName() {
    return "IntelliJ";
  }

  @Override
  protected String getPrefix() {
    return isWindowsNativeLook() ? "intellijlaf_native" : "intellijlaf";
  }

  @Override
  protected BasicLookAndFeel createBaseLookAndFeel() {
    if (isWindowsNativeLook()) {
      try {
        final String name = UIManager.getSystemLookAndFeelClassName();
        return (BasicLookAndFeel)Class.forName(name).newInstance();
      }
      catch (Exception e) {
        log(e);
      }
    }
    return super.createBaseLookAndFeel();
  }

  @Override
  protected DefaultMetalTheme createMetalTheme() {
    return new IdeaBlueMetalTheme();
  }

  @Override
  public UIDefaults getDefaults() {
    UIDefaults defaults = super.getDefaults();
    if (SystemInfo.isMacOSYosemite) {
      installMacOSXFonts(defaults);
    }
    return defaults;
  }

  private static void installMacOSXFonts(UIDefaults defaults) {
    final String face = "HelveticaNeue-Regular";
    final FontUIResource uiFont = getFont(face, 13, Font.PLAIN);
    LafManagerImpl.initFontDefaults(defaults, 13, uiFont);
    for (Object key : new HashSet<>(defaults.keySet())) {
      Object value = defaults.get(key);
      if (value instanceof FontUIResource) {
        FontUIResource font = (FontUIResource)value;
        if (font.getFamily().equals("Lucida Grande") || font.getFamily().equals("Serif")) {
          if (!key.toString().contains("Menu")) {
            defaults.put(key, getFont(face, font.getSize(), font.getStyle()));
          }
        }
      }
    }

    FontUIResource uiFont11 = getFont(face, 11, Font.PLAIN);
    defaults.put("TableHeader.font", uiFont11);

    FontUIResource buttonFont = getFont("HelveticaNeue-Medium", 13, Font.PLAIN);
    defaults.put("Button.font", buttonFont);
    Font menuFont = getFont("Lucida Grande", 14, Font.PLAIN);
    defaults.put("Menu.font", menuFont);
    defaults.put("MenuItem.font", menuFont);
    defaults.put("MenuItem.acceleratorFont", menuFont);
    defaults.put("PasswordField.font", defaults.getFont("TextField.font"));
  }

  @NotNull
  private static FontUIResource getFont(String yosemite, int size, @JdkConstants.FontStyle int style) {
    if (SystemInfo.isMacOSElCapitan) {
      Font font = FontInfo.get(".SF NS Display").getFont();
      if (font != null) {
        return new FontUIResource(font.deriveFont(style, size));
      }
    }
    return new FontUIResource(yosemite, style, size);
  }

  public static boolean isGraphite() {
    if (!SystemInfo.isMac) return false;
    try {
      // https://developer.apple.com/library/mac/documentation/Cocoa/Reference/ApplicationKit/Classes/NSCell_Class/index.html#//apple_ref/doc/c_ref/NSGraphiteControlTint
      // NSGraphiteControlTint = 6
      return Foundation.invoke("NSColor", "currentControlTint").intValue() == 6;
    } catch (Exception e) {
      return false;
    }
  }

  public static Color getSelectedControlColor() {
    // https://developer.apple.com/library/mac/e/Cocoa/Reference/ApplicationKit/Classes/NSColor_Class/#//apple_ref/occ/clm/NSColor/alternateSelectedControlColor
    return MacUtil.colorFromNative(Foundation.invoke("NSColor", "alternateSelectedControlColor"));
  }

  public static boolean isWindowsNativeLook() {
    return SystemInfo.isWindows && Registry.is("ide.intellij.laf.win10.ui");
  }
}
