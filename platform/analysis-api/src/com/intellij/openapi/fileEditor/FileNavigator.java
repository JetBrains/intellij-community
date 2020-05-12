// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface FileNavigator {
  static FileNavigator getInstance() {
    return ServiceManager.getService(FileNavigator.class);
  }

  default boolean canNavigate(@NotNull VirtualFile file) {
    return file.isValid();
  }

  void navigate(@NotNull OpenFileDescriptor descriptor, boolean requestFocus);
  boolean navigateInEditor(@NotNull OpenFileDescriptor descriptor, boolean requestFocus);
}
