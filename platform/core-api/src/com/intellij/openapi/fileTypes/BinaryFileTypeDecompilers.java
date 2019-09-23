// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

@Service
public final class BinaryFileTypeDecompilers extends FileTypeExtension<BinaryFileDecompiler> {
  private BinaryFileTypeDecompilers() {
    super("com.intellij.filetype.decompiler");
  }

  public static BinaryFileTypeDecompilers getInstance() {
    return ApplicationManager.getApplication().getService(BinaryFileTypeDecompilers.class);
  }
}