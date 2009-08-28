package com.intellij.ide.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorHelper {
  @Nullable
  public static Editor openInEditor(@NotNull PsiElement element) {
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
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, false);
  }
}