// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EditorHelper {
  public static <T extends PsiElement> void openFilesInEditor(T @NotNull [] elements) {
    final int limit = EditorWindow.Companion.getTabLimit();
    final int max = Math.min(limit, elements.length);
    for (int i = 0; i < max; i++) {
      openInEditor(elements[i], true, true);
    }
  }

  public static Editor openInEditor(@NotNull PsiElement element) {
    FileEditor editor = openInEditor(element, true);
    return editor instanceof TextEditor ? ((TextEditor)editor).getEditor() : null;
  }

  public static @Nullable Editor openInMaybeInjectedEditor(@NotNull PsiElement element) {
    Editor editor = openInEditor(element);
    if (editor == null) return null;
    return getInjectedEditor(editor, element);
  }

  /**
   * Returns editor for injected language if {@code element} is inside injected fragment, top-level editor otherwise
   * May return null if there's no editor for a given element
   */
  public static @Nullable Editor getMaybeInjectedEditor(@NotNull PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;

    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return null;

    FileEditor fileEditor = FileEditorManager.getInstance(element.getProject()).getSelectedEditor(virtualFile);
    if (fileEditor instanceof TextEditor textEditor) {
      Editor editor = textEditor.getEditor();
      if (virtualFile instanceof VirtualFileWindow) {
        editor = getInjectedEditor(editor, containingFile);
      }
      return editor;
    } else {
      return null;
    }
  }

  public static @Nullable FileEditor openInEditor(@NotNull PsiElement element, boolean switchToText) {
    return openInEditor(element, switchToText, false);
  }

  public static @Nullable FileEditor openInEditor(@NotNull PsiElement element, boolean switchToText, boolean focusEditor) {
    PsiFile file;
    int offset;
    if (element instanceof PsiFile){
      file = (PsiFile)element;
      offset = -1;
    }
    else{
      file = element.getContainingFile();
      offset = element.getTextOffset();
    }
    if (file == null) return null;//SCR44414
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    OpenFileDescriptor descriptor = new OpenFileDescriptor(element.getProject(), virtualFile, offset);
    Project project = element.getProject();
    if (offset == -1 && !switchToText) {
      FileEditorManager.getInstance(project).openEditor(descriptor, focusEditor);
    }
    else {
      FileEditorManager.getInstance(project).openTextEditor(descriptor, focusEditor);
    }
    return FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
  }

  private static @NotNull Editor getInjectedEditor(@NotNull Editor editor, @NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, file);
  }
}