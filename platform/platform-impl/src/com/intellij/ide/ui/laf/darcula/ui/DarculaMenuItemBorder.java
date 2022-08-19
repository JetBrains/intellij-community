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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaMenuItemBorder implements Border, UIResource {

  public static @NotNull JBInsets menuBarItemInnerInsets() {
    return JBUI.insets(2);
  }

  public static @NotNull JBInsets menuBarItemOuterInsets() {
    return JBUI.insets(0);
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) { }

  @Override
  public Insets getBorderInsets(Component c) {
    JBInsets result;

    if (IdeaPopupMenuUI.isPartOfPopupMenu(c)) {
      result = JBUI.CurrentTheme.PopupMenu.Selection.innerInsets();
      if (ExperimentalUI.isNewUI()) {
        result.top = 0;
        result.bottom = 0;

        // For backward compatibility outerInsets is NOT part of border in old UI
        result = JBInsets.addInsets(result, JBUI.CurrentTheme.PopupMenu.Selection.outerInsets());
      }
    }
    else if (IdeaPopupMenuUI.isMenuBarItem(c)) {
      result = menuBarItemInnerInsets();
    }
    else {
      result = JBUI.CurrentTheme.Menu.Selection.innerInsets();
      if (ExperimentalUI.isNewUI()) {
        result.top = 0;
        result.bottom = 0;
        result = JBInsets.addInsets(result, JBUI.CurrentTheme.Menu.Selection.outerInsets());
      }
    }

    return result.asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
