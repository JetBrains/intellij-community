// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class FilePromptMacro extends PromptingMacro implements SecondQueueExpandMacro, PathMacro {
  @Override
  public @NotNull String getName() {
    return "FilePrompt";
  }

  @Override
  public @NotNull String getDescription() {
    return ExecutionBundle.message("shows.a.file.chooser.dialog");
  }

  @Override
  protected String promptUser(@NotNull DataContext dataContext,
                              @Nls @Nullable String label, @Nullable String defaultValue) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    if (label != null) {
      descriptor.setTitle(label);
    }
    final VirtualFile[] result = IdeUiService.getInstance().chooseFiles(descriptor, project, null);
    return result.length == 1? FileUtil.toSystemDependentName(result[0].getPath()) : null;
  }
}
