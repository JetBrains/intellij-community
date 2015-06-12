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
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
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
    return "intellijlaf";
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
    FontUIResource font = new FontUIResource("HelveticaNeue-CondensedBlack", Font.PLAIN, 13);
    defaults.put("Label.font", font);
    defaults.put("CheckBox.font", font);
    defaults.put("RadioButton.font", font);
    defaults.put("ComboBox.font", font);
  }

  public static boolean isGraphite() {
    Color c = UIManager.getColor("controlHighlight");
    return c != null && c.getBlue() < 150;
  }
}
