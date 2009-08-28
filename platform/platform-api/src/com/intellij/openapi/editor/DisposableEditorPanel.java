package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
*/
public class DisposableEditorPanel extends JPanel implements Disposable {
  private final Editor myEditor;

  public DisposableEditorPanel(Editor editor) {
    super(new BorderLayout());
    myEditor = editor;
    add(editor.getComponent(), BorderLayout.CENTER);
  }

  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }
}
