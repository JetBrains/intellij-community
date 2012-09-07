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

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ui.GroupedElementsRenderer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 9/6/12 2:30 PM
 */
public class ArrangementColorsService {

  @NotNull private Color myNormalTextColor;
  @NotNull private Color mySelectedTextColor;
  @NotNull private Color myNormalBackgroundColor;
  @NotNull private Color mySelectedBackgroundColor;
  @NotNull private Color myNormalBorderColor;
  @NotNull private Color mySelectedBorderColor;

  public ArrangementColorsService(@NotNull LafManager lafManager) {
    lafManager.addLafManagerListener(new LafManagerListener() {
      @Override
      public void lookAndFeelChanged(LafManager source) {
        updateColors();
      }
    });
    updateColors();
  }

  @NotNull
  public Color getTextColor(boolean selected) {
    return selected ? mySelectedTextColor : myNormalTextColor;
  }

  @NotNull
  public Color getBackgroundColor(boolean selected) {
    return selected ? mySelectedBackgroundColor : myNormalBackgroundColor;
  }

  @NotNull
  public Color getBorderColor(boolean selected) {
    return selected ? mySelectedBorderColor : myNormalBorderColor;
  }
  
  private void updateColors() {
    myNormalTextColor = UIUtil.getTreeTextForeground();
    mySelectedTextColor = UIUtil.getTreeSelectionForeground();
    myNormalBackgroundColor = UIUtil.getPanelBackground();
    mySelectedBackgroundColor = UIUtil.getTreeSelectionBackground();
    myNormalBorderColor = UIUtil.getBorderColor();
    Color selectionBorderColor = UIUtil.getTreeSelectionBorderColor();
    if (selectionBorderColor == null) {
      selectionBorderColor = GroupedElementsRenderer.SELECTED_FRAME_FOREGROUND;
    }
    mySelectedBorderColor = selectionBorderColor;
  }
}
