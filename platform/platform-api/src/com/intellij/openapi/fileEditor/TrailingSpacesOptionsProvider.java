// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TrailingSpacesOptionsProvider {
  ExtensionPointName<TrailingSpacesOptionsProvider> EP_NAME = new ExtensionPointName<>("com.intellij.trailingSpacesOptionsProvider");

  @Nullable
  TrailingSpacesOptions getOptions(@NotNull Project project, @NotNull VirtualFile file);
}
