// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * JBProtocolRevisionResolver handle revision parameter in navigation request of JBProtocol.
 */
public interface JBProtocolRevisionResolver {
  ExtensionPointName<JBProtocolRevisionResolver> EP_NAME = ExtensionPointName.create("com.intellij.jbProtocolRevisionResolver");

  /**
   * Implementations of this method are used for handling revision parameter in JBProtocolNavigateCommand.
   * First not null VirtualFile instance will be used as navigation target.
   *
   * @param project - The project to navigate in.
   * @param absolutePath - The absolute path to target file.
   * @param revision - The revision to navigate.
   * @return - target of JBProtocolNavigateCommand to open in editor or null if resolver can't handle this request.
   */
  @Nullable
  VirtualFile resolve(@NotNull Project project, @NotNull String absolutePath, @NotNull String revision);

  static @Nullable VirtualFile processResolvers(@NotNull Project project, @NotNull String absolutePath, @NotNull String revision) {
    for (JBProtocolRevisionResolver resolver : EP_NAME.getExtensions()) {
      VirtualFile file = resolver.resolve(project, absolutePath, revision);
      if (file != null) return file;
    }
    return null;
  }
}
