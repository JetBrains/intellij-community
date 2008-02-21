package com.intellij.psi.statistics;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;

/**
 * @author yole
 */
public abstract class JavaStatisticsManager extends StatisticsManager {
  public static JavaStatisticsManager getJavaInstance() {
    return (JavaStatisticsManager) ApplicationManager.getApplication().getComponent(StatisticsManager.class);
  }

  public static NameContext getContext(final PsiElement element) {
    if(element instanceof PsiField){
      if(((PsiField)element).hasModifierProperty(PsiModifier.STATIC) && ((PsiField)element).hasModifierProperty(PsiModifier.FINAL))
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

  public abstract int getMemberUseCount(PsiType qualifierType, PsiMember member);
  public abstract void incMemberUseCount(PsiType qualifierType, PsiMember member);

  public abstract String[] getNameSuggestions(PsiType type, JavaStatisticsManager.NameContext context, String prefix);
  public abstract void incNameUseCount(PsiType type, JavaStatisticsManager.NameContext context, String name);
}
