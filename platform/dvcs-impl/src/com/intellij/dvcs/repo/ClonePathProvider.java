// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides default paths for clone repository functionality.
 * Default implementation suggests to clone a project to the `[project dir]/[last url component]`.
 */
public abstract class ClonePathProvider {
  private static final ExtensionPointName<ClonePathProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.clonePathProvider");

  public static @NotNull String defaultParentDirectoryPath(@NotNull Project project, @NotNull DvcsRememberedInputs rememberedInputs) {
    for (ClonePathProvider provider : EP_NAME.getExtensionList()) {
      String directoryPath = provider.getParentDirectoryPath(project, rememberedInputs);
      if (StringUtil.isNotEmpty(directoryPath)) {
        return directoryPath;
      }
    }

    String parentDirectory = rememberedInputs.getCloneParentDir();
    return StringUtil.isEmptyOrSpaces(parentDirectory) ? ProjectUtil.getBaseDir() : parentDirectory;
  }

  public static @NotNull String relativeDirectoryPathForVcsUrl(@NotNull Project project, @NotNull String vcsUrl) {
    for (ClonePathProvider provider : EP_NAME.getExtensionList()) {
      String directoryPath = provider.getRelativeDirectoryPathForVcsUrl(project, vcsUrl);
      if (StringUtil.isNotEmpty(directoryPath)) {
        return directoryPath;
      }
    }
    String encoded = PathUtil.getFileName(vcsUrl);
    try {
      return URLUtil.decode(encoded);
    }
    catch (Exception e) {
      return encoded;
    }
  }

  public abstract @Nullable String getParentDirectoryPath(@NotNull Project project, @NotNull DvcsRememberedInputs rememberedInputs);

  public abstract @Nullable String getRelativeDirectoryPathForVcsUrl(@NotNull Project project, @NotNull String vcsUrl);
}
