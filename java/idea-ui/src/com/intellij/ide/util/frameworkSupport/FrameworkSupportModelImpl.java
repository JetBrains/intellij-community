// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.frameworkSupport;

import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import org.jetbrains.annotations.NotNull;

public class FrameworkSupportModelImpl extends FrameworkSupportModelBase {
  private final String myContentRootPath;

  public FrameworkSupportModelImpl(final @NotNull Project project,
                                   @NotNull String baseDirectoryForLibrariesPath,
                                   @NotNull LibrariesContainer librariesContainer) {
    super(project, null, librariesContainer);
    myContentRootPath = baseDirectoryForLibrariesPath;
  }

  @Override
  public @NotNull String getBaseDirectoryForLibrariesPath() {
    return myContentRootPath;
  }
}
