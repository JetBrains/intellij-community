// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class TempUIThemeBasedLookAndFeelInfo extends UIThemeBasedLookAndFeelInfo {
  private UIManager.LookAndFeelInfo myPreviousLaf;

  public TempUIThemeBasedLookAndFeelInfo(UITheme theme) {
    super(theme);
    myPreviousLaf = LafManager.getInstance().getCurrentLookAndFeel();
    if (myPreviousLaf instanceof TempUIThemeBasedLookAndFeelInfo) {
      myPreviousLaf = ((TempUIThemeBasedLookAndFeelInfo)myPreviousLaf).getPreviousLaf();
    }
  }

  public UIManager.LookAndFeelInfo getPreviousLaf() {
    return myPreviousLaf;
  }

}
