// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @see BinaryFileDecompiler
 */
@Service
public final class BinaryFileTypeDecompilers extends FileTypeExtension<BinaryFileDecompiler> {
  private static final ExtensionPointName<KeyedLazyInstance<BinaryFileDecompiler>> EP_NAME =
    new ExtensionPointName<>("com.intellij.filetype.decompiler");

  private BinaryFileTypeDecompilers() {
    super(EP_NAME);
    Application app = ApplicationManager.getApplication();
    if (!app.isUnitTestMode()) {
      EP_NAME.addChangeListener(() -> notifyDecompilerSetChange(), app);
    }
  }

  public void notifyDecompilerSetChange() {
    ApplicationManager.getApplication().invokeLater(() -> FileDocumentManager.getInstance().reloadBinaryFiles(), ModalityState.nonModal());
  }

  public static BinaryFileTypeDecompilers getInstance() {
    return ApplicationManager.getApplication().getService(BinaryFileTypeDecompilers.class);
  }

  /**
   * Allows executing a computation while temporarily enabling decompilation on the Event Dispatch Thread (EDT).
   * For the duration of the computation, decompilation on EDT is allowed, and it is automatically disabled
   * afterward, ensuring thread safety and proper cleanup.
   *
   * @param computation the computation to be executed with decompilation on EDT permitted.
   *                    Must not be null.
   * @return the result of the computation.
   * @deprecated Redesign the logic - move to BGT with the progress-bar.
   */
  @ApiStatus.Internal
  @Deprecated
  public <T> T allowDecompileOnEDT(@NotNull Computable<T> computation) {
    isAllowDecompileOnEDT(true);
    try {
      return computation.compute();
    }
    finally {
      isAllowDecompileOnEDT(false);
    }
  }

  private final ThreadLocal<Boolean> myAllowDecompileOnEDT = ThreadLocal.withInitial(() -> false);

  @ApiStatus.Internal
  public boolean isAllowDecompileOnEDT() {
    return myAllowDecompileOnEDT.get();
  }

  private void isAllowDecompileOnEDT(boolean enabled) {
    boolean oldValue = myAllowDecompileOnEDT.get();
    assert oldValue != enabled : "Non-paired myAllowDecompileOnEDT mode";
    myAllowDecompileOnEDT.set(enabled);
  }
}
