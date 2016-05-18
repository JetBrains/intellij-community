/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaInstaller {

  public static void uninstall() {
    performImpl(false);
  }

  public static void install() {
    performImpl(true);
  }

  private static void performImpl(boolean b) {
    JBColor.setDark(b);
    IconLoader.setUseDarkIcons(b);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme current = colorsManager.getGlobalScheme();
    if (b != ColorUtil.isDark(current.getDefaultBackground())) {
      String targetScheme = b ? DarculaLaf.NAME : EditorColorsScheme.DEFAULT_SCHEME_NAME;
      EditorColorsScheme scheme = colorsManager.getScheme(targetScheme);
      if (scheme != null) {
        colorsManager.setGlobalScheme(scheme);
      }
    }
    update();
  }

  protected static void update() {
    UISettings.getInstance().fireUISettingsChanged();
    ActionToolbarImpl.updateAllToolbarsImmediately();
  }
}
