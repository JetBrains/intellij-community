// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBScrollBar;

import javax.swing.plaf.ScrollBarUI;

public class GlobalScrollBar extends JBScrollBar {

  private static final Logger LOG = Logger.getInstance(GlobalScrollBar.class);
  private final EditorModel myEditorModel;

  public GlobalScrollBar(EditorModel editorModel) {
    myEditorModel = editorModel;
  }

  @Override
  public void updateUI() {
    ScrollBarUI ui = getUI();
    if (ui instanceof GlobalScrollBarUI/* || ui instanceof MyDefaultScrollBarUI*/) {
      return;
    }
    //setUI(SystemInfo.isMac ? new GlobalScrollBarUI() : new MyDefaultScrollBarUI());
    //setUI(new GlobalScrollBarUI());
    setUI(SystemInfo.isMac ? new GlobalScrollBarUI(14, 14, 11)
                           : new GlobalScrollBarUI());

    setOpaque(true);
  }

  public void fireValueChangedFromOutside() {
    myEditorModel.fireGlobalScrollBarValueChangedFromOutside(getValue());
  }
}
