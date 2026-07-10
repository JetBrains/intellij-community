// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public abstract class GlobalUsageHelper {
  final Map<PsiClass,Boolean> unusedClassCache = new HashMap<>();

  public abstract boolean shouldCheckUsages(@NotNull PsiMember member);
  public abstract boolean isLocallyUsed(@NotNull PsiNamedElement member);
  public abstract boolean isCurrentFileAlreadyChecked();
  public abstract boolean ignoreTestUsages();
}
