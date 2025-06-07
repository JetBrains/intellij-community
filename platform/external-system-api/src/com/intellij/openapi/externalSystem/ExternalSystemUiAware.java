// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ExternalSystemUiAware {

  @NotNull
  @Nls String getProjectRepresentationName(@NotNull String targetProjectPath, @Nullable String rootProjectPath);

  default @NotNull @Nls String getProjectRepresentationName(@NotNull Project project, @NotNull String targetProjectPath, @Nullable String rootProjectPath){
    return getProjectRepresentationName(targetProjectPath, rootProjectPath);
  }

  @Nullable
  FileChooserDescriptor getExternalProjectConfigDescriptor();

  @Nullable
  Icon getProjectIcon();

  @Nullable
  Icon getTaskIcon();
}
