// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.util.KeyedLazyInstance;

@Service
public final class BinaryFileTypeDecompilers extends FileTypeExtension<BinaryFileDecompiler> {
  private static final ExtensionPointName<KeyedLazyInstance<BinaryFileDecompiler>> EP_NAME =
    new ExtensionPointName<>("com.intellij.filetype.decompiler");

  private BinaryFileTypeDecompilers() {
    super(EP_NAME);
    EP_NAME.addChangeListener(() -> notifyDecompilersChange(), null);
  }

  public void notifyDecompilersChange() {
    ApplicationManager.getApplication().invokeLater(() -> FileDocumentManager.getInstance().reloadBinaryFiles(), ModalityState.NON_MODAL);
  }

  public static BinaryFileTypeDecompilers getInstance() {
    return ApplicationManager.getApplication().getService(BinaryFileTypeDecompilers.class);
  }
}