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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementColorsAware;
import com.intellij.ui.GroupedElementsRenderer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 10/24/12 4:25 PM
 */
public class ArrangementColorsProviderImpl implements ArrangementColorsProvider {

  @Nullable private final ArrangementColorsAware myColorsAware;

  @NotNull private final Map<ArrangementSettingType, Color> myTextColors
    = new EnumMap<ArrangementSettingType, Color>(ArrangementSettingType.class);
  @NotNull private final Map<ArrangementSettingType, Color> myBackgroundColors
    = new EnumMap<ArrangementSettingType, Color>(ArrangementSettingType.class);
  @NotNull private final Map<ArrangementSettingType, Color> mySelectedTextColors
    = new EnumMap<ArrangementSettingType, Color>(ArrangementSettingType.class);
  @NotNull private final Map<ArrangementSettingType, Color> mySelectedBackgroundColors
    = new EnumMap<ArrangementSettingType, Color>(ArrangementSettingType.class);

  @NotNull private Color myBorderColor;
  @NotNull private Color mySelectedBorderColor;

  public ArrangementColorsProviderImpl(@Nullable ArrangementColorsAware colorsAware) {
    myColorsAware = colorsAware;
    applyDefaultColors();
    if (colorsAware != null) {
      applyCustomColors(colorsAware);
    }
  }

  @NotNull
  @Override
  public Color getBorderColor(boolean selected) {
    return selected ? myBorderColor : mySelectedBorderColor;
  }

  @NotNull
  @Override
  public Color getTextColor(@NotNull ArrangementSettingType type, boolean selected) {
    return selected ? mySelectedTextColors.get(type) : myTextColors.get(type);
  }

  @NotNull
  @Override
  public Color getTextBackgroundColor(@NotNull ArrangementSettingType type, boolean selected) {
    return selected ? mySelectedBackgroundColors.get(type) : myBackgroundColors.get(type);
  }

  /**
   * Asks the implementation to ensure that it uses the most up-to-date colors.
   * <p/>
   * I.e. this method is assumed to be called when color settings has been changed and gives a chance to reflect the changes
   * accordingly.
   */
  public void refresh() {
    if (myColorsAware == null) {
      return;
    }

    myTextColors.clear();
    myBackgroundColors.clear();
    mySelectedTextColors.clear();
    mySelectedBackgroundColors.clear();
    
    applyDefaultColors();
    applyCustomColors(myColorsAware);
  }
  
  private void applyDefaultColors() {
    Color textColor = UIUtil.getTreeTextForeground();
    Color selectedTextColor = UIUtil.getTreeSelectionForeground();
    Color backgroundColor = UIUtil.getPanelBackground();
    Color selectedBackgroundColor = UIUtil.getTreeSelectionBackground();
    for (ArrangementSettingType type : ArrangementSettingType.values()) {
      myTextColors.put(type, textColor);
      mySelectedTextColors.put(type, selectedTextColor);
      myBackgroundColors.put(type, backgroundColor);
      mySelectedBackgroundColors.put(type, selectedBackgroundColor);
    }
    
    myBorderColor = UIUtil.getBorderColor();
    Color selectionBorderColor = UIUtil.getTreeSelectionBorderColor();
    if (selectionBorderColor == null) {
      selectionBorderColor = GroupedElementsRenderer.SELECTED_FRAME_FOREGROUND;
    }
    mySelectedBorderColor = selectionBorderColor;
  }

  private void applyCustomColors(@NotNull ArrangementColorsAware colorsAware) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    for (ArrangementSettingType type : ArrangementSettingType.values()) {
      applyColorIfPossible(scheme, colorsAware.getTextKey(type, false), type, myTextColors);
      applyColorIfPossible(scheme, colorsAware.getTextKey(type, true), type, mySelectedTextColors);
      applyColorIfPossible(scheme, colorsAware.getTextBackgroundKey(type, false), type, myBackgroundColors);
      applyColorIfPossible(scheme, colorsAware.getTextBackgroundKey(type, true), type, mySelectedBackgroundColors);
    }

    Color borderColor = colorsAware.getBorderColor(false);
    if (borderColor != null) {
      myBorderColor = borderColor;
    }
    Color selectedBorderColor = colorsAware.getBorderColor(true);
    if (selectedBorderColor != null) {
      mySelectedBorderColor = selectedBorderColor;
    }
  }

  private static void applyColorIfPossible(@NotNull EditorColorsScheme scheme,
                                           @Nullable TextAttributesKey key,
                                           @NotNull ArrangementSettingType type,
                                           @NotNull Map<ArrangementSettingType, Color> holder)
  {
    if (key == null) {
      return;
    }
    TextAttributes attributes = scheme.getAttributes(key);
    if (attributes == null) {
      return;
    }
    Color color = attributes.getForegroundColor();
    if (color != null) {
      holder.put(type, color);
    }
  }
}
