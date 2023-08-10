// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.UnsafeWeakList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

final class InjectedEditorWindowTrackerImpl extends InjectedEditorWindowTracker {
  private final Collection<EditorWindowImpl> allEditors = new UnsafeWeakList<>(); // guarded by allEditors

  @NotNull
  Editor createEditor(final @NotNull DocumentWindowImpl documentRange,
                      final @NotNull EditorImpl editor,
                      final @NotNull PsiFile injectedFile) {
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
      editor.executeNonCancelableBlock(()-> {
        EditorWindowImpl newEditorWindow = new EditorWindowImpl(documentRange, editor, injectedFile, documentRange.isOneLine());
        editorWindow.set(newEditorWindow);
        allEditors.add(newEditorWindow);
        newEditorWindow.assertValid();
      });
    }
    return editorWindow.get();
  }

  @Override
  void disposeInvalidEditors() {
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
  void disposeEditorFor(@NotNull DocumentWindow documentWindow) {
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