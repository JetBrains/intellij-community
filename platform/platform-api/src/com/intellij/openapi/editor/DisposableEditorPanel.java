// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.*;

public class DisposableEditorPanel extends JPanel implements Disposable {
  private final Editor myEditor;

  public DisposableEditorPanel(Editor editor) {
    super(new BorderLayout());
    myEditor = editor;
    add(editor.getComponent(), BorderLayout.CENTER);
  }

  @Override
  public void dispose() {
    if (! myEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
  }
}
