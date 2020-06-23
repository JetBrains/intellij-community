// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
public final class BinaryFileTypeDecompilers extends FileTypeExtension<BinaryFileDecompiler> {
  private BinaryFileTypeDecompilers() {
    super("com.intellij.filetype.decompiler");
  }

  public static BinaryFileTypeDecompilers getInstance() {
    return ApplicationManager.getApplication().getService(BinaryFileTypeDecompilers.class);
  }

  public void addExtensionPointChangeListener(@NotNull Runnable listener, @Nullable Disposable parentDisposable) {
    ExtensionPoint<KeyedLazyInstance<BinaryFileDecompiler>> extensionPoint = getPoint();
    if (extensionPoint != null) {
      extensionPoint.addChangeListener(listener, parentDisposable);
    }
  }
}