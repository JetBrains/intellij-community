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
package com.intellij.openapi.editor.colors;

import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author gregsh
 */
public class EditorColorsUtil {
  private EditorColorsUtil() {
  }

  /**
   * @return the appropriate color scheme for UI other than text editor (QuickDoc, UsagesView, etc.)
   * depending on the current LAF and current editor color scheme.
   */
  @NotNull
  public static EditorColorsScheme getGlobalOrDefaultColorScheme() {
    return getColorSchemeForBackground(null);
  }

  @Nullable
  public static Color getGlobalOrDefaultColor(@NotNull ColorKey colorKey) {
    Color color = getColorSchemeForBackground(null).getColor(colorKey);
    return color != null? color : colorKey.getDefaultColor();
  }

  /**
   * @return the appropriate color scheme for UI other than text editor (QuickDoc, UsagesView, etc.)
   * depending on the current LAF, current editor color scheme and the component background.
   */
  @NotNull
  public static EditorColorsScheme getColorSchemeForComponent(@Nullable JComponent component) {
    return getColorSchemeForBackground(component != null ? component.getBackground() : null);
  }

  /**
   * @return the appropriate color scheme for UI other than text editor (QuickDoc, UsagesView, etc.)
   * depending on the current LAF, current editor color scheme and background color.
   */
  public static EditorColorsScheme getColorSchemeForBackground(@Nullable Color background) {
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    boolean dark1 = background == null ? UIUtil.isUnderDarcula() : ColorUtil.isDark(background);
    boolean dark2 = ColorUtil.isDark(globalScheme.getDefaultBackground());
    if (dark1 != dark2) {
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
      if (scheme != null) {
        return scheme;
      }
    }
    return globalScheme;
  }
}
