// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class EditorLockFreeTyping {
  public static final Key<Boolean> USE_UI_PSI_FOR_DOCUMENT_KEY = Key.create("USE_UI_PSI_FOR_DOCUMENT_KEY");

  public static boolean isEnabled() {
    return Registry.is("editor.lockfree.typing.enabled", false);
  }

  public static boolean isPsiInteractionAllowed() {
    return !isEnabled() || isLockFreePsiSupported();
  }

  private static boolean isLockFreePsiSupported() {
    // TODO: one day it should become `true`, see IJPL-236269
    return false;
  }
}
