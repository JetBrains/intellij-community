// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.fileTypes.FileTypeExtension;

public final class FileTypeFileViewProviders extends FileTypeExtension<FileViewProviderFactory> {
  public static final FileTypeFileViewProviders INSTANCE = new FileTypeFileViewProviders();

  private FileTypeFileViewProviders() {
    super("com.intellij.fileType.fileViewProviderFactory");
  }
}