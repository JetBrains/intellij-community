// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.fileTypes.FileTypeExtension;

public final class FileTypeFileViewProviders extends FileTypeExtension<FileViewProviderFactory> {
  public static final FileTypeFileViewProviders INSTANCE = new FileTypeFileViewProviders();

  private FileTypeFileViewProviders() {
    super("com.intellij.fileType.fileViewProviderFactory");
  }
}