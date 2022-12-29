// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.psi.PsiElement;
import com.intellij.psi.Weigher;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.NotNull;

public abstract class ProximityWeigher extends Weigher<PsiElement, ProximityLocation> {
  @Override
  public abstract Comparable<?> weigh(@NotNull PsiElement element, @NotNull ProximityLocation location);
}
