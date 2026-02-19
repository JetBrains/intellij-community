// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.UnsafeWeakList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

@ApiStatus.Internal
public final class InjectedEditorWindowTrackerImpl extends InjectedEditorWindowTracker {
  private final Collection<EditorWindowImpl> allEditors = new UnsafeWeakList<>(); // guarded by allEditors

  @NotNull
  @Override
  public Editor createEditor(final @NotNull DocumentWindow documentRange,
                             final @NotNull Editor editor,
                             final @NotNull PsiFile injectedFile) {
    if (!(editor instanceof EditorImpl editorImpl)) return editor;
    var documentRangeImpl = (DocumentWindowImpl)documentRange;
    assert documentRange.isValid();
    assert injectedFile.isValid();
    Ref<EditorWindowImpl> editorWindow = Ref.create();
    synchronized (allEditors) {
      for (EditorWindowImpl oldEditorWindow : allEditors) {
        if (oldEditorWindow.getDocument() == documentRange && oldEditorWindow.getDelegate() == editor) {
          oldEditorWindow.myInjectedFile = injectedFile;
          if (oldEditorWindow.isValid()) {
            return oldEditorWindow;
          }
        }
      }
      editorImpl.executeNonCancelableBlock(()-> {
        EditorWindowImpl newEditorWindow = new EditorWindowImpl(documentRangeImpl, editorImpl, injectedFile, documentRange.isOneLine());
        editorWindow.set(newEditorWindow);
        allEditors.add(newEditorWindow);
        newEditorWindow.assertValid();
      });
    }
    return editorWindow.get();
  }

  @Override
  public void disposeInvalidEditors() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    synchronized (allEditors) {
      Iterator<EditorWindowImpl> iterator = allEditors.iterator();
      while (iterator.hasNext()) {
        EditorWindowImpl editorWindow = iterator.next();
        if (!editorWindow.isValid()) {
          editorWindow.dispose();
          iterator.remove();
        }
      }
    }
  }

  @Override
  public void disposeEditorFor(@NotNull DocumentWindow documentWindow) {
    synchronized (allEditors) {
      for (Iterator<EditorWindowImpl> iterator = allEditors.iterator(); iterator.hasNext(); ) {
        EditorWindowImpl editor = iterator.next();
        if (InjectionRegistrarImpl.intersect(editor.getDocument(), (DocumentWindowImpl)documentWindow)) {
          editor.dispose();
          iterator.remove();
        }
      }
    }
  }
}