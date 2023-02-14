// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * Provides a check whether it is allowed for intention actions to execute certain types of side effects,
 * e.g. modify the project model.
 */
@ApiStatus.Internal
public interface SideEffectGuard {
  static <T, E extends Throwable> T computeWithoutSideEffects(ThrowableComputable<T, E> supplier) throws E {
    return computeWithAllowedSideEffects(SideEffectGuardImpl.NO_EFFECTS, supplier);
  }

  static <T, E extends Throwable> T computeWithAllowedSideEffects(EnumSet<EffectType> effects, ThrowableComputable<T, E> supplier) throws E {
    EnumSet<EffectType> previous = SideEffectGuardImpl.ourSideEffects.get();
    SideEffectGuardImpl.ourSideEffects.set(effects.clone());
    try {
      return supplier.compute();
    }
    finally {
      SideEffectGuardImpl.ourSideEffects.set(previous);
    }
  }

  static void checkSideEffectAllowed(@NotNull EffectType effectType) {
    if (!SideEffectGuardImpl.isAllowed(effectType)) {
      throw new SideEffectNotAllowedException(effectType);
    }
  }

  final class SideEffectNotAllowedException extends IllegalStateException implements ControlFlowException {
    public SideEffectNotAllowedException(@NotNull EffectType effectType) {
      super("Side effect not allowed: " + effectType.name());
    }
  }

  enum EffectType {
    /**
     * Change project model
     */
    PROJECT_MODEL,
    /**
     * Change settings
     */
    SETTINGS,
    /**
     * Execute external process
     */
    EXEC,
    /**
     * Spawn an action in UI thread
     */
    INVOKE_LATER,
  }
}
