// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.largeFilesEditor.systemBuilding.PluginSystemBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SingleRootFileViewProvider;
import org.jetbrains.annotations.NotNull;

public class EditorProvider implements FileEditorProvider, DumbAware {

  public static final String PROVIDER_ID = "EditorProvider";

  private static Logger logger = Logger.getInstance(EditorProvider.class);

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return TextEditorProvider.isTextFile(file) && SingleRootFileViewProvider.isTooLargeForContentLoading(file);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    //return new EditorManagerImpl(project, file);
    PluginSystemBuilder builder = PluginSystemBuilder.getInstance();
    RootComponent rootComponent = builder.build(project, file);
    return rootComponent.getEditorManager();
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return PROVIDER_ID;
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
