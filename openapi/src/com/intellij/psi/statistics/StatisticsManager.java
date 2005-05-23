/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.statistics;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.psi.*;

import java.util.Map;

public abstract class StatisticsManager implements SettingsSavingComponent {
  public static NameContext getContext(final PsiElement element) {
    if(element instanceof PsiField){
      if(((PsiField)element).hasModifierProperty("static") && ((PsiField)element).hasModifierProperty("final"))
        return NameContext.CONSTANT_NAME;
      return NameContext.FIELD_NAME;
    }
    if(element instanceof PsiLocalVariable) {
      return NameContext.LOCAL_VARIABLE_NAME;
    }
    return null;
  }

  public enum NameContext{
    LOCAL_VARIABLE_NAME,
    FIELD_NAME,
    CONSTANT_NAME
  }
  public static StatisticsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(StatisticsManager.class);
  }

  public abstract int getMemberUseCount(PsiType qualifierType, PsiMember member, Map<PsiType, PsiType> normalizedItems);
  public abstract void incMemberUseCount(PsiType qualifierType, PsiMember member);

  public abstract String[] getNameSuggestions(PsiType type, NameContext context, String prefix);
  public abstract void incMemberUseCount(PsiType type, NameContext context, String name);
}
