// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ArrayUtil.getFirstElement;

/**
 * @author Konstantin Bulenkov
 */
public class FileSelectInContext implements SelectInContext {
  private final Project myProject;
  private final VirtualFile myFile;
  private final FileEditorProvider myProvider;

  public FileSelectInContext(@NotNull Project project, @NotNull VirtualFile file) {
    this(project, file, getFileEditorProvider(project, file));
  }

  public FileSelectInContext(@NotNull PsiDirectory directory) {
    this(directory.getProject(), directory.getVirtualFile(), null);
  }

  public FileSelectInContext(@NotNull Project project, @NotNull VirtualFile file, @Nullable FileEditorProvider provider) {
    myProject = project;
    myFile = BackedVirtualFile.getOriginFileIfBacked(file);
    myProvider = provider;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull VirtualFile getVirtualFile() {
    return myFile;
  }

  @Override
  public @Nullable Object getSelectorInFile() {
    return null;
  }

  @Override
  public @Nullable FileEditorProvider getFileEditorProvider() {
    return myProvider;
  }

  private static FileEditorProvider getFileEditorProvider(@NotNull Project project, @NotNull VirtualFile file) {
    FileEditorManager manager = FileEditorManager.getInstance(project);
    return manager == null ? null : () -> getFirstElement(manager.openFile(file, false));
  }

  @Override
  public String toString() {
    return "FileSelectInContext{" +
           "myProject=" + myProject +
           ", myFile=" + myFile +
           ", myProvider=" + myProvider +
           '}';
  }
}
