// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SameModuleWeigher extends ProximityWeigher {

  @Override
  public Comparable weigh(final @NotNull PsiElement element, final @NotNull ProximityLocation location) {
    final Module elementModule = ModuleUtil.findModuleForPsiElement(element);
    if (location.getPositionModule() == elementModule) {
      return 2;
    }

    if (elementModule != null) {
      return 1; // in project => still not bad
    }

    return 0;
  }
}
