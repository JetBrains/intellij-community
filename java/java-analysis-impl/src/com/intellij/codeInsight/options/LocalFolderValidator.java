// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInspection.ui.StringValidatorWithSwingSelector;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class LocalFolderValidator implements StringValidatorWithSwingSelector {
  @NlsContexts.DialogTitle private final String myTitle;

  public LocalFolderValidator(@NlsContexts.DialogTitle String title) {
    myTitle = title;
  }
  
  @Override
  public @Nullable String select(@NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // Unable to instantiate chooser in tests
      return null;
    }
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.singleDir();
    descriptor.setTitle(myTitle);
    descriptor.setForcedToUseIdeaFileChooser(true);
    final VirtualFile newRoot = FileChooser.chooseFile(descriptor, project, null);
    return newRoot == null ? null : newRoot.getPath();
  }

  @Override
  public @NotNull String validatorId() {
    return "fs.folder";
  }

  @Override
  public @Nullable String getErrorMessage(@Nullable Project project, @NotNull String filePath) {
    Path file;
    try {
      file = Path.of(filePath);
    }
    catch (InvalidPathException e) {
      return e.getMessage();
    }
    VirtualFile virtualFile = VfsUtil.findFile(file, true);
    if (virtualFile == null) return JavaBundle.message("validator.text.directory.not.found");
    if (!virtualFile.isDirectory()) return JavaBundle.message("validator.text.not.directory");
    return null;
  }
}
