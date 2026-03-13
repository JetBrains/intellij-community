// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;


@Internal
public final class EditorLockFreeTyping {
  private static final Key<Boolean> USE_UI_PSI_FOR_DOCUMENT_KEY = Key.create("USE_UI_PSI_FOR_DOCUMENT_KEY");

  public static boolean isEnabled() {
    return Registry.is("editor.lockfree.typing.enabled", false);
  }

  public static boolean isPsiInteractionAllowed() {
    return !isEnabled() || isLockFreePsiSupported();
  }

  @RequiresEdt
  public static void withUiPsiScope(@NotNull Document document, @NotNull Runnable action) {
    if (isEnabled()) {
      document.putUserData(USE_UI_PSI_FOR_DOCUMENT_KEY, true);
      try {
        action.run();
      } finally {
        document.putUserData(USE_UI_PSI_FOR_DOCUMENT_KEY, null);
      }
    } else {
      action.run();
    }
  }

  public static boolean isInUiPsiScope(@NotNull Document document) {
    if (EDT.isCurrentThreadEdt()) {
      return document.getUserData(USE_UI_PSI_FOR_DOCUMENT_KEY) == Boolean.TRUE;
    }
    return false;
  }

  private static boolean isLockFreePsiSupported() {
    // TODO: one day it should become `true`, see IJPL-236269
    return false;
  }
}
