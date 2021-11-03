// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;


public class FilePromptMacro extends PromptingMacro implements SecondQueueExpandMacro, PathMacro {
  @NotNull
  @Override
  public String getName() {
    return "FilePrompt";
  }

  @NotNull
  @Override
  public String getDescription() {
    return ExecutionBundle.message("shows.a.file.chooser.dialog");
  }

  @Override
  protected String promptUser(DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    final VirtualFile[] result = IdeUiService.getInstance().chooseFiles(descriptor, project, null);
    return result.length == 1? FileUtil.toSystemDependentName(result[0].getPath()) : null;
  }

  @Override
  public void cachePreview(@NotNull DataContext dataContext) {
    myCachedPreview = "<filename>";
  }
}
