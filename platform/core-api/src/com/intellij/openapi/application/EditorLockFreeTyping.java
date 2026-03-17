// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@Internal
public final class EditorLockFreeTyping {
  private static final Key<Boolean> USE_UI_PSI_FOR_DOCUMENT_KEY = Key.create("USE_UI_PSI_FOR_DOCUMENT_KEY");

  public static boolean isEnabled() {
    return Registry.is("editor.lockfree.typing.enabled", false);
  }

  @RequiresEdt
  public static void withElfScope(@NotNull Document elfDocument, @NotNull Runnable action) {
    if (isEnabled()) {
      VirtualFile elfVirtualFile = FileDocumentManager.getInstance().getFile(elfDocument);
      USE_UI_PSI_FOR_DOCUMENT_KEY.set(elfVirtualFile, true);
      USE_UI_PSI_FOR_DOCUMENT_KEY.set(elfDocument, true);
      try {
        action.run();
      } finally {
        USE_UI_PSI_FOR_DOCUMENT_KEY.set(elfVirtualFile, null);
        USE_UI_PSI_FOR_DOCUMENT_KEY.set(elfDocument, null);
      }
    } else {
      action.run();
    }
  }

  public static boolean isInElfScope(@Nullable Document document) {
    return document != null && EDT.isCurrentThreadEdt() && USE_UI_PSI_FOR_DOCUMENT_KEY.isIn(document);
  }

  public static boolean isInElfScope(@Nullable VirtualFile virtualFile) {
    return virtualFile != null && EDT.isCurrentThreadEdt() && USE_UI_PSI_FOR_DOCUMENT_KEY.isIn(virtualFile);
  }

  public static void assertReadAccess(@NotNull VirtualFile virtualFile) {
    if (isEnabled()) {
      if (!isInElfScope(virtualFile)) {
        ThreadingAssertions.assertReadAccess();
      }
    } else {
      ThreadingAssertions.assertReadAccess();
    }
  }
}
