// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.editor;

import com.intellij.ui.components.JBScrollBar;
import org.jetbrains.annotations.ApiStatus;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

@ApiStatus.Internal
public final class GlobalScrollBar extends JBScrollBar {

  private final EditorModel myEditorModel;

  public GlobalScrollBar(EditorModel editorModel) {
    myEditorModel = editorModel;

    MyMouseAdapter mouseAdapter = new MyMouseAdapter();
    addMouseListener(mouseAdapter);
    addMouseMotionListener(mouseAdapter);

    // to make thumb always visible
    setOpaque(true);
  }

  public void fireValueChangedFromOutside() {
    myEditorModel.fireGlobalScrollBarValueChangedFromOutside(getValue());
  }


  private final class MyMouseAdapter extends MouseAdapter {

    @Override
    public void mouseReleased(MouseEvent e) {
      fireValueChangedFromOutside();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      fireValueChangedFromOutside();
    }
  }
}
