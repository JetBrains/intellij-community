// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface JBProtocolNavigateResolver {
  ExtensionPointName<JBProtocolNavigateResolver> EP_NAME = ExtensionPointName.create("com.intellij.jbProtocolNavigateResolver");

  @Nullable
  VirtualFile resolve(@NotNull String absolutePath, @NotNull Project project, @NotNull Map<String, String> parameters);

  @Nullable
  static VirtualFile processEnhancers(@NotNull String absolutePath, @NotNull Project project, @NotNull Map<String, String> parameters) {
    for (JBProtocolNavigateResolver enhancer : EP_NAME.getExtensions()) {
      VirtualFile file = enhancer.resolve(absolutePath, project, parameters);
      if (file != null) return file;
    }
    return null;
  }
}
