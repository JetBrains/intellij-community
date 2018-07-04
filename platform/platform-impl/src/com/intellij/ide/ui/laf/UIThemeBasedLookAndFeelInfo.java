// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class UIThemeBasedLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  private final UITheme myTheme;

  public UIThemeBasedLookAndFeelInfo(UITheme theme) {
    super(theme.getName(), theme.isDark() ? DarculaLaf.class.getName() : IntelliJLaf.class.getName());
    myTheme = theme;
  }

  public UITheme getTheme() {
    return myTheme;
  }

  public void installTheme(UIDefaults defaults) {
    myTheme.applyProperties(defaults);
    IconPathPatcher patcher = myTheme.getPatcher();
    if (patcher != null) {
      IconLoader.installPathPatcher(patcher);
    }
  }

  public void dispose() {
    IconPathPatcher patcher = myTheme.getPatcher();
    if (patcher != null) {
      IconLoader.removePathPatcher(patcher);
    }
  }
}
