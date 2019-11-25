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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LightEditorManager implements Disposable {
  private final List<LightEditorInfo> myEditors = new ArrayList<>();
  private final EventDispatcher<LightEditorListener> myEventDispatcher = EventDispatcher.create(LightEditorListener.class);

  final static Key<Boolean> NO_IMPLICIT_SAVE = Key.create("light.edit.no.implicit.save");

  @NotNull
  private LightEditorInfo createEditor(@NotNull Document document, @NotNull VirtualFile file) {
    Editor editor = EditorFactory.getInstance().createEditor(
      document, ProjectManager.getInstance().getDefaultProject(), EditorKind.MAIN_EDITOR);
    ObjectUtils.consumeIfCast(editor, EditorImpl.class,
                              editorImpl -> editorImpl.setDropHandler(new LightEditDropHandler()));
    final LightEditorInfo editorInfo = new LightEditorInfo(editor, file);
    myEditors.add(editorInfo);
    return editorInfo;
  }

  @Nullable
  LightEditorInfo createEditor(@NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      document.putUserData(NO_IMPLICIT_SAVE, false);
      LightEditorInfo editorInfo = createEditor(document, file);
      Editor editor = editorInfo.getEditor();
      if (editor instanceof EditorEx) ((EditorEx)editor).setHighlighter(getHighlighter(file, editor));
      return editorInfo;
    }
    return null;
  }

  @Override
  public void dispose() {
    myEditors.stream()
      .filter(editorInfo -> !editorInfo.getEditor().isDisposed())
      .forEach(editorInfo -> EditorFactory.getInstance().releaseEditor(editorInfo.getEditor()));
    myEditors.clear();
  }

  void closeEditor(@NotNull LightEditorInfo editorInfo) {
    Editor editor = editorInfo.getEditor();
    myEditors.remove(editorInfo);
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

  @Nullable
  LightEditorInfo findOpen(@NotNull VirtualFile file) {
    return myEditors.stream()
      .filter(editorInfo -> file.getPath().equals(editorInfo.getFile().getPath()))
      .findFirst().orElse(null);
  }

  boolean isImplicitSaveAllowed(@NotNull Document document) {
    return ObjectUtils.notNull(document.getUserData(NO_IMPLICIT_SAVE), true);
  }
}
