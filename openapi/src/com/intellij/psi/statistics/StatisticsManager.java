/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.statistics;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiType;

import java.util.Map;

public abstract class StatisticsManager implements SettingsSavingComponent {
  public static StatisticsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(StatisticsManager.class);
  }

  public abstract int getMemberUseCount(PsiType qualifierType, PsiMember member, Map<PsiType, PsiType> normalizedItems);
  public abstract void incMemberUseCount(PsiType qualifierType, PsiMember member);
}
