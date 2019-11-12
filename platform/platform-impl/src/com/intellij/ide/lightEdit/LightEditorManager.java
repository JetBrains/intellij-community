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
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LightEditorManager implements Disposable {
  private final List<Editor> myEditors = new ArrayList<>();
  private final EventDispatcher<LightEditorListener> myEventDispatcher = EventDispatcher.create(LightEditorListener.class);

  @NotNull
  Editor createEditor(@NotNull Document document) {
    Editor editor = EditorFactory.getInstance().createEditor(
      document, ProjectManager.getInstance().getDefaultProject(), EditorKind.MAIN_EDITOR);
    ObjectUtils.consumeIfCast(editor, EditorImpl.class,
                              editorImpl -> editorImpl.setDropHandler(new LightEditDropHandler()));
    myEditors.add(editor);
    return editor;
  }

  @Nullable
  Editor createEditor(@NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      Editor editor = createEditor(document);
      if (editor instanceof EditorEx) ((EditorEx)editor).setHighlighter(getHighlighter(file, editor));
      return editor;
    }
    return null;
  }

  @Override
  public void dispose() {
    myEditors.stream()
      .filter(editor -> !editor.isDisposed())
      .forEach(editor -> EditorFactory.getInstance().releaseEditor(editor));
    myEditors.clear();
  }

  void closeEditor(@NotNull LightEditorInfo editorInfo) {
    Editor editor = editorInfo.getEditor();
    myEditors.remove(editor);
    if (!editor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(editor);
    }
    myEventDispatcher.getMulticaster().afterClose(editorInfo);
  }

  public void addListener(@NotNull LightEditorListener listener) {
    myEventDispatcher.addListener(listener);
  }

  void fireEditorSelected(@Nullable LightEditorInfo editorInfo) {
    myEventDispatcher.getMulticaster().afterSelect(editorInfo);
  }

  @NotNull
  private static EditorHighlighter getHighlighter(@NotNull VirtualFile file, @NotNull Editor editor) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(file, editor.getColorsScheme(), null);
  }

  int getEditorCount() {
    return myEditors.size();
  }
}
