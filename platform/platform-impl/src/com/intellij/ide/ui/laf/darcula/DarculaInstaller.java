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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
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
  private static final String DARCULA_EDITOR_THEME_KEY = "Darcula.savedEditorTheme";
  private static final String DEFAULT_EDITOR_THEME_KEY = "Default.savedEditorTheme";

  public static void uninstall() {
    performImpl(false);
  }

  public static void install() {
    performImpl(true);
  }

  private static void performImpl(boolean dark) {
    JBColor.setDark(dark);
    IconLoader.setUseDarkIcons(dark);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme current = colorsManager.getGlobalScheme();
    if (dark != ColorUtil.isDark(current.getDefaultBackground())) {
      String targetScheme = dark ? DarculaLaf.NAME : EditorColorsScheme.DEFAULT_SCHEME_NAME;
      PropertiesComponent properties = PropertiesComponent.getInstance();
      String savedEditorThemeKey = dark ? DARCULA_EDITOR_THEME_KEY : DEFAULT_EDITOR_THEME_KEY;
      String toSavedEditorThemeKey = dark ? DEFAULT_EDITOR_THEME_KEY : DARCULA_EDITOR_THEME_KEY;
      String themeName = properties.getValue(savedEditorThemeKey);
      if (themeName != null && colorsManager.getScheme(themeName) != null) {
        targetScheme = themeName;
      }
      properties.setValue(toSavedEditorThemeKey, current.getName(), dark ? EditorColorsScheme.DEFAULT_SCHEME_NAME : DarculaLaf.NAME);

      EditorColorsScheme scheme = colorsManager.getScheme(targetScheme);
      if (scheme != null) {
        colorsManager.setGlobalScheme(scheme);
      }
    }
    update();
  }

  protected static void update() {
    UISettings.getShadowInstance().fireUISettingsChanged();
    ActionToolbarImpl.updateAllToolbarsImmediately();
  }
}
