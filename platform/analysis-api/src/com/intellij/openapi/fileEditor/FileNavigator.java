// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public interface FileNavigator {
  static FileNavigator getInstance() {
    return ApplicationManager.getApplication().getService(FileNavigator.class);
  }

  default boolean canNavigate(@NotNull OpenFileDescriptor descriptor) {
    return descriptor.getFile().isValid();
  }

  default boolean canNavigateToSource(@NotNull OpenFileDescriptor descriptor) {
    return descriptor.getFile().isValid();
  }

  void navigate(@NotNull OpenFileDescriptor descriptor, boolean requestFocus);

  boolean navigateInEditor(@NotNull OpenFileDescriptor descriptor, boolean requestFocus);
}
