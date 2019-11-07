// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;


import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class LightEditPanel extends JPanel implements Disposable {

  private Editor myEditor;

  public LightEditPanel() {
    setLayout(new BorderLayout());
    Document document = new DocumentImpl("");
    myEditor = createEditor(document);
    add(myEditor.getComponent(), BorderLayout.CENTER);
  }

  private static Editor createEditor(Document document) {
    return EditorFactory
      .getInstance().createEditor(document, ProjectManager.getInstance().getDefaultProject(), EditorKind.MAIN_EDITOR);
  }

  @NotNull
  private EditorHighlighter getHighlighter(@NotNull VirtualFile file) {
    return EditorHighlighterFactory
      .getInstance().createEditorHighlighter(file, myEditor.getColorsScheme(), null);
  }

  public void loadFile(@NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      if (myEditor != null) {
        remove(myEditor.getComponent());
      }
      releaseCurrentEditor();
      myEditor = createEditor(document);
      if (myEditor instanceof EditorEx) ((EditorEx)myEditor).setHighlighter(getHighlighter(file));
      add(myEditor.getComponent(), BorderLayout.CENTER);
    }
  }

  @Override
  public void dispose() {
    releaseCurrentEditor();
  }

  private void releaseCurrentEditor() {
    if (myEditor != null) {
      if (!myEditor.isDisposed()) {
        EditorFactory.getInstance().releaseEditor(myEditor);
      }
      myEditor = null;
    }
  }
}
