// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.DefaultScrollBarUI;
import com.intellij.ui.components.JBScrollBar;

import javax.swing.*;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;

public class LocalInvisibleScrollBar extends JBScrollBar {

  private static final Logger LOG = Logger.getInstance(LocalInvisibleScrollBar.class);

  private final EditorModel myEditorModel;

  public LocalInvisibleScrollBar(EditorModel editorModel) {
    super();
    myEditorModel = editorModel;
    setModel(new MyBoundedRangeModel());
  }

  @Override
  public void updateUI() {
    ScrollBarUI ui = getUI();
    if (ui instanceof MyScrollBarUI) return;
    setUI(new MyScrollBarUI());
  }

  private class MyScrollBarUI extends DefaultScrollBarUI {

    @Override
    public void paint(Graphics g, JComponent c) {
      // say NO to "paint" =)
      // TODO: 2019-04-12 just remove line below to make scrollbar invisible, but active
      super.paint(g, c);
    }
  }

  private class MyBoundedRangeModel extends DefaultBoundedRangeModel {

    @Override
    public void setRangeProperties(int newValue, int newExtent, int newMin, int newMax, boolean adjusting) {
      int oldValue = getValue();
      super.setRangeProperties(newValue, newExtent, newMin, newMax, adjusting);
      int delta = newValue - oldValue;
      myEditorModel.fireLocalScrollBarValueChanged();
    }
  }
}