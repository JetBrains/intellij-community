// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface FileTypeIndentOptionsFactory {
  @NotNull
  CommonCodeStyleSettings.IndentOptions createIndentOptions();

  @NotNull
  FileType getFileType();
}