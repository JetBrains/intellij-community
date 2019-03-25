// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.repo;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLDecoder;

/**
 * Provides default paths for clone repository functionality.
 * Default implementation suggests to clone a project to the `[project dir]/[last url component]`.
 */
public abstract class ClonePathProvider {
  private static final ExtensionPointName<ClonePathProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.clonePathProvider");

  @NotNull
  public static String defaultDirectoryToClone(@NotNull Project project, @NotNull DvcsRememberedInputs rememberedInputs) {
    for (ClonePathProvider provider : EP_NAME.getExtensionList()) {
      String directoryPath = provider.getDirectoryPathToClone(project, rememberedInputs);
      if (StringUtil.isNotEmpty(directoryPath)) {
        return directoryPath;
      }
    }

    String parentDirectory = rememberedInputs.getCloneParentDir();
    return StringUtil.isEmptyOrSpaces(parentDirectory) ? ProjectUtil.getBaseDir() : parentDirectory;
  }

  @NotNull
  public static String directoryPathToCloneForUrl(@NotNull Project project, @NotNull String vcsUrl) {
    for (ClonePathProvider provider : EP_NAME.getExtensionList()) {
      String directoryPath = provider.getDirectoryPathToCloneForUrl(project, vcsUrl);
      if (StringUtil.isNotEmpty(directoryPath)) {
        return directoryPath;
      }
    }
    return safeUrlDecode(PathUtil.getFileName(vcsUrl));
  }

  @NotNull
  private static String safeUrlDecode(@NotNull String encoded) {
    try {
      return URLDecoder.decode(encoded, CharsetToolkit.UTF8);
    }
    catch (Exception e) {
      return encoded;
    }
  }

  @Nullable
  public abstract String getDirectoryPathToClone(@NotNull Project project, @NotNull DvcsRememberedInputs rememberedInputs);

  @Nullable
  public abstract String getDirectoryPathToCloneForUrl(@NotNull Project project, @NotNull String vcsUrl);
}
