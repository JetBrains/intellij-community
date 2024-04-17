// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.proximity.ProximityStatistician;
import org.jetbrains.annotations.NotNull;

public final class JavaProximityStatistician extends ProximityStatistician{
  @Override
  public StatisticsInfo serialize(final @NotNull PsiElement element, final @NotNull ProximityLocation location) {
    return element instanceof PsiMember ? JavaStatisticsManager.createInfo(null, (PsiMember)element) : null;
  }
}
