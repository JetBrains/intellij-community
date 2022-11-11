// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

class SideEffectGuardImpl {
  static final EnumSet<SideEffectGuard.EffectType> NO_EFFECTS = EnumSet.noneOf(SideEffectGuard.EffectType.class);
  static final EnumSet<SideEffectGuard.EffectType> ALL_EFFECTS = EnumSet.allOf(SideEffectGuard.EffectType.class);
  static final ThreadLocal<EnumSet<SideEffectGuard.EffectType>> ourSideEffects = ThreadLocal.withInitial(() -> ALL_EFFECTS);

  static boolean isAllowed(@NotNull SideEffectGuard.EffectType effectType) {
    return ourSideEffects.get().contains(effectType);
  }
}
