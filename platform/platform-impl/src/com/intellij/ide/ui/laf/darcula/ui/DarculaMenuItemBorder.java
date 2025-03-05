// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ShowMode;
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
    return JBUI.emptyInsets();
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
      result = ShowMode.Companion.isMergedMainMenu() ? JBUI.insets(0, 4) : menuBarItemInnerInsets();
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
