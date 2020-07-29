// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author pegov
 */
public final class TabsUtil {
  public static final JBValue TAB_VERTICAL_PADDING = new JBValue.Float(2);
  public static final int NEW_TAB_VERTICAL_PADDING = JBUIScale.scale(2);
  private static final @NonNls String FAKE_LABEL_TEXT = "XXX";

  private TabsUtil() {
  }

  public static int getTabsHeight() {
    return getTabsHeight(NEW_TAB_VERTICAL_PADDING);
  }

  public static int getTabsHeight(int verticalPadding) {
    JLabel xxx = new JLabel(FAKE_LABEL_TEXT);
    xxx.setFont(getLabelFont());
    return xxx.getPreferredSize().height + (verticalPadding * 2);
  }

  public static Font getLabelFont() {
    UISettings uiSettings = UISettings.getInstance();
    Font font = JBUI.CurrentTheme.ToolWindow.headerFont();
    if (uiSettings.getOverrideLafFonts()) {
      return font.deriveFont((float)uiSettings.getFontSize() + JBUI.CurrentTheme.ToolWindow.overrideHeaderFontSizeOffset());
    }

    return font;
  }
}
