package com.intellij.execution.configurations;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;

/**
 * User: anna
 * Date: Jan 26, 2005
 */
public class ConfigurationUtil {
  public static Condition<PsiClass> PUBLIC_INSTANTIATABLE_CLASS = new Condition<PsiClass>() {
    public boolean value(final PsiClass psiClass) {
      if (!MAIN_CLASS.value(psiClass)) return false;
      if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
      if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) return false;
      return true;
    }
  };
  public static Condition<PsiClass> MAIN_CLASS = new Condition<PsiClass>() {
    public boolean value(final PsiClass psiClass) {
      if (psiClass instanceof PsiAnonymousClass) return false;
      if (psiClass.isInterface()) return false;
      if (psiClass.getContainingClass() != null &&
          !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      return true;
    }
  };
}
