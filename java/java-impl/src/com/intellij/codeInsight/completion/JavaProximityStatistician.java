/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.psi.util.proximity.ProximityStatistician;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;

/**
 * @author peter
 */
public class JavaProximityStatistician extends ProximityStatistician{
  public StatisticsInfo serialize(final PsiElement element, final ProximityLocation location) {
    return element instanceof PsiMember ? JavaStatisticsManager.createInfo(null, (PsiMember)element) : null;
  }
}
