// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class UIThemeBasedLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  private UITheme myTheme;

  public UIThemeBasedLookAndFeelInfo(UITheme theme) {
    super(theme.getName(), theme.isDark() ? DarculaLaf.class.getName() : IntelliJLaf.class.getName());
    myTheme = theme;
  }

  public void installTheme(UIDefaults defaults) {

  }

  public void uninstallTheme(UIDefaults defaults) {

  }
}
