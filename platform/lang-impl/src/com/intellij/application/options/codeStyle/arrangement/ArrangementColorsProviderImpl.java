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

  @NotNull private final Map<ArrangementSettingType, TextAttributes> myTextAttributes
    = new EnumMap<ArrangementSettingType, TextAttributes>(ArrangementSettingType.class);
  @NotNull private final Map<ArrangementSettingType, TextAttributes> mySelectedTextAttributes
    = new EnumMap<ArrangementSettingType, TextAttributes>(ArrangementSettingType.class);

  @NotNull private Color myBorderColor;
  @NotNull private Color mySelectedBorderColor;
  @NotNull private Color myRowUnderMouseBackgroundColor;

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
  public TextAttributes getTextAttributes(@NotNull ArrangementSettingType type, boolean selected) {
    return selected ? mySelectedTextAttributes.get(type) : myTextAttributes.get(type);
  }

  @NotNull
  @Override
  public Color getRowUnderMouseBackground() {
    return myRowUnderMouseBackgroundColor;
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

    myTextAttributes.clear();
    mySelectedTextAttributes.clear();
    
    applyDefaultColors();
    applyCustomColors(myColorsAware);
  }
  
  private void applyDefaultColors() {
    TextAttributes normalTextAttributes = new TextAttributes();
    normalTextAttributes.setForegroundColor(UIUtil.getTreeTextForeground());
    normalTextAttributes.setBackgroundColor(UIUtil.getPanelBackground());
    
    TextAttributes selectedTextAttributes = new TextAttributes();
    selectedTextAttributes.setForegroundColor(UIUtil.getTreeSelectionForeground());
    selectedTextAttributes.setBackgroundColor(UIUtil.getTreeSelectionBackground());
    
    for (ArrangementSettingType type : ArrangementSettingType.values()) {
      myTextAttributes.put(type, normalTextAttributes);
      mySelectedTextAttributes.put(type, selectedTextAttributes);
    }
    
    myBorderColor = UIUtil.getBorderColor();
    Color selectionBorderColor = UIUtil.getTreeSelectionBorderColor();
    if (selectionBorderColor == null) {
      selectionBorderColor = GroupedElementsRenderer.SELECTED_FRAME_FOREGROUND;
    }
    mySelectedBorderColor = selectionBorderColor;

    myRowUnderMouseBackgroundColor = UIUtil.getPanelBackground();
  }

  private void applyCustomColors(@NotNull ArrangementColorsAware colorsAware) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    for (ArrangementSettingType type : ArrangementSettingType.values()) {
      TextAttributes textAttributes = colorsAware.getTextAttributes(scheme, type, false);
      if (textAttributes != null) {
        myTextAttributes.put(type, textAttributes);
      }

      TextAttributes selectedTextAttributes = colorsAware.getTextAttributes(scheme, type, true);
      if (selectedTextAttributes != null) {
        mySelectedTextAttributes.put(type, selectedTextAttributes);
      }
    }

    Color borderColor = colorsAware.getBorderColor(scheme, false);
    if (borderColor != null) {
      myBorderColor = borderColor;
    }
    
    Color selectedBorderColor = colorsAware.getBorderColor(scheme, true);
    if (selectedBorderColor != null) {
      mySelectedBorderColor = selectedBorderColor;
    }

    Color activeRowBackground = colorsAware.getRowUnderMouseBackground(scheme);
    if (activeRowBackground != null) {
      myRowUnderMouseBackgroundColor = activeRowBackground;
    }
  }
}
