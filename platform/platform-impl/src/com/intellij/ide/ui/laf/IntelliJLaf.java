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

import javax.swing.*;
import javax.swing.plaf.basic.BasicLookAndFeel;
import javax.swing.plaf.metal.DefaultMetalTheme;
import java.awt.*;

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
