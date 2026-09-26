// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface FileTypeConsumer {
  String EXTENSION_DELIMITER = ";";

  void consume(@NotNull FileType fileType);

  void consume(@NotNull FileType fileType, @NonNls @NotNull String semicolonDelimitedExtensions);

  void consume(@NotNull FileType fileType, @NotNull FileNameMatcher @NotNull ... matchers);

  @Nullable
  FileType getStandardFileTypeByName(@NonNls @NotNull String name);
}
