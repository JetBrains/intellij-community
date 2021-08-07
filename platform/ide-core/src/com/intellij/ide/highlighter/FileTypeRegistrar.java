// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

public interface FileTypeRegistrar {
  ExtensionPointName<FileTypeRegistrar> EP_NAME = ExtensionPointName.create("com.intellij.fileTypeRegistrar");

  void initFileType(@NotNull FileType fileType);
}
