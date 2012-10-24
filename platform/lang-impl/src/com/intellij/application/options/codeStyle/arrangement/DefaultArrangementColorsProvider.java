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

import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 10/23/12 11:50 PM
 */
public class DefaultArrangementColorsProvider extends AbstractArrangementColorsProvider {

  @NotNull private Color myNormalTextColor;
  @NotNull private Color mySelectedTextColor;
  @NotNull private Color myNormalBackgroundColor;
  @NotNull private Color mySelectedBackgroundColor;
  
  @NotNull
  @Override
  public Color getTextColor(@NotNull ArrangementSettingType type) {
    return myNormalTextColor;
  }

  @NotNull
  @Override
  public Color getTextBackgroundColor(@NotNull ArrangementSettingType type) {
    return myNormalBackgroundColor;
  }

  @NotNull
  @Override
  public Color getSelectedTextColor(@NotNull ArrangementSettingType type) {
    return mySelectedTextColor;
  }

  @NotNull
  @Override
  public Color getSelectedTextBackgroundColor(@NotNull ArrangementSettingType type) {
    return mySelectedBackgroundColor;
  }

  @Override
  public void updateColors() {
    super.updateColors();
    myNormalTextColor = UIUtil.getTreeTextForeground();
    mySelectedTextColor = UIUtil.getTreeSelectionForeground();
    myNormalBackgroundColor = UIUtil.getPanelBackground();
    mySelectedBackgroundColor = UIUtil.getTreeSelectionBackground();
  }
}
