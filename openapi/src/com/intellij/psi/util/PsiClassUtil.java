package com.intellij.psi.util;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;

/**
 * @author mike
 */
public class PsiClassUtil {
  private PsiClassUtil() {
  }

  public static boolean isRunnableClass(final PsiClass aClass, final boolean mustBePublic) {
    if (aClass instanceof PsiAnonymousClass) return false;
    if (aClass.isInterface()) return false;
    if (mustBePublic && !aClass.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) || aClass.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    return aClass.getContainingClass() == null || aClass.hasModifierProperty(PsiModifier.STATIC);
  }
}
