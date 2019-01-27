// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class TempUIThemeBasedLookAndFeelInfo extends UIThemeBasedLookAndFeelInfo {
  private final VirtualFile myEditorSchemeFile;
  private UIManager.LookAndFeelInfo myPreviousLaf;

  public TempUIThemeBasedLookAndFeelInfo(UITheme theme, VirtualFile themeJson) {
    super(theme);
    myEditorSchemeFile = themeJson;
    myPreviousLaf = LafManager.getInstance().getCurrentLookAndFeel();
    if (myPreviousLaf instanceof TempUIThemeBasedLookAndFeelInfo) {
      myPreviousLaf = ((TempUIThemeBasedLookAndFeelInfo)myPreviousLaf).getPreviousLaf();
    }
  }

  public UIManager.LookAndFeelInfo getPreviousLaf() {
    return myPreviousLaf;
  }

  @Override
  protected void installEditorScheme() {
    String name = getTheme().getEditorScheme();
    if (name != null && myEditorSchemeFile != null) {
      EditorColorsManager cm = EditorColorsManager.getInstance();
      ((EditorColorsManagerImpl)cm).getSchemeManager().loadBundledScheme(myEditorSchemeFile.getPath(), this);
      EditorColorsScheme scheme = cm.getScheme(getTheme().getEditorSchemeName());
      if (scheme != null) {
        cm.setGlobalScheme(scheme);
      }
    }

  }
}
