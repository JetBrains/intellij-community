// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * If {@link com.intellij.openapi.fileEditor.FileEditorProvider} implements this interface, it will be used instead of
 * creating a temp FileEditor just to call {@link FileEditor#getStructureViewBuilder()}.
 */
public interface StructureViewFileEditorProvider {
  @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull Project project, @NotNull VirtualFile file);
}
