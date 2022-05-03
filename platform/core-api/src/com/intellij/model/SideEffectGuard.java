// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Provides a check whether it is allowed for intention actions to execute certain types of side effects,
 * e.g. modify the project model.
 */
@ApiStatus.Internal
public interface SideEffectGuard {
  boolean isAllowed(@NotNull EffectType effectType);

  static void checkSideEffectAllowed(@NotNull EffectType effectType) {
    SideEffectGuard guard = ApplicationManager.getApplication().getService(SideEffectGuard.class);
    if (guard != null && !guard.isAllowed(effectType)) {
      throw new SideEffectNotAllowedException(effectType);
    }
  }

  final class SideEffectNotAllowedException extends IllegalStateException implements ControlFlowException {
    public SideEffectNotAllowedException(@NotNull EffectType effectType) {
      super("Side effect not allowed: " + effectType.name());
    }
  }

  enum EffectType {
    PROJECT_MODEL,
    SETTINGS,
    EXEC,
  }
}
