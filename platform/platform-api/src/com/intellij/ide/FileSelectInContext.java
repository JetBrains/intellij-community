/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ArrayUtil.getFirstElement;

/**
 * @author Konstantin Bulenkov
 * @author Sergey Malenkov
 */
public class FileSelectInContext implements SelectInContext {
  private final Project myProject;
  private final VirtualFile myFile;
  private final FileEditorProvider myProvider;

  public FileSelectInContext(@NotNull Project project, @NotNull VirtualFile file) {
    this(project, file, getFileEditorProvider(project, file));
  }

  public FileSelectInContext(@NotNull Project project, @NotNull VirtualFile file, @Nullable FileEditorProvider provider) {
    myProject = project;
    myFile = BackedVirtualFile.getOriginFileIfBacked(file);
    myProvider = provider;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public VirtualFile getVirtualFile() {
    return myFile;
  }

  @Nullable
  @Override
  public Object getSelectorInFile() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorProvider getFileEditorProvider() {
    return myProvider;
  }

  private static FileEditorProvider getFileEditorProvider(@NotNull Project project, @NotNull VirtualFile file) {
    FileEditorManager manager = FileEditorManager.getInstance(project);
    return manager == null ? null : () -> getFirstElement(manager.openFile(file, false));
  }
}
