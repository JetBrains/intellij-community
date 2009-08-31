package com.intellij.ide.hierarchy.method;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.MethodSignature;

final class MethodHierarchyUtil {
  public static PsiMethod findBaseMethodInClass(final PsiMethod baseMethod, final PsiClass aClass, final boolean checkBases) {
    if (baseMethod == null) return null; // base method is invalid
    if (cannotBeOverridding(baseMethod)) return null;
    /*if (!checkBases) return MethodSignatureUtil.findMethodBySignature(aClass, signature, false);*/
    return MethodSignatureUtil.findMethodBySuperMethod(aClass, baseMethod, checkBases);
    /*final MethodSignatureBackedByPsiMethod signature = SuperMethodsSearch.search(baseMethod, aClass, checkBases, false).findFirst();
    return signature == null ? null : signature.getMethod();*/
  }

  private static boolean cannotBeOverridding(final PsiMethod method) {
    final PsiClass parentClass = method.getContainingClass();
    return parentClass == null
           || method.isConstructor()
           || method.hasModifierProperty(PsiModifier.STATIC)
           || method.hasModifierProperty(PsiModifier.PRIVATE);
  }

}
