// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import icons.ExternalSystemIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * This class is not singleton but offers single-point-of-usage field - {@link #INSTANCE}.
 */
public class DefaultExternalSystemUiAware implements ExternalSystemUiAware {

  public static final @NotNull DefaultExternalSystemUiAware INSTANCE = new DefaultExternalSystemUiAware();

  @Override
  public @NotNull String getProjectRepresentationName(@NotNull String targetProjectPath, @Nullable String rootProjectPath) {
    return new File(targetProjectPath).getParentFile().getName();
  }

  @Override
  public @Nullable FileChooserDescriptor getExternalProjectConfigDescriptor() {
    return null;
  }

  @Override
  public @NotNull Icon getProjectIcon() {
    return AllIcons.Nodes.IdeaProject;
  }

  @Override
  public @NotNull Icon getTaskIcon() {
    return ExternalSystemIcons.Task;
  }
}
