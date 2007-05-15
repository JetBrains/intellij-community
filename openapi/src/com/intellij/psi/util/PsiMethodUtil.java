package com.intellij.psi.util;

import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public class PsiMethodUtil {
  private PsiMethodUtil() {
  }

  @Nullable
  public static PsiMethod findMainMethod(final PsiClass aClass) {
    final PsiMethod[] mainMethods = aClass.findMethodsByName("main", false);
    return findMainMethod(mainMethods);
  }

  @Nullable
  public static PsiMethod findMainMethod(final PsiMethod[] mainMethods) {
    for (final PsiMethod mainMethod : mainMethods) {
      if (isMainMethod(mainMethod)) return mainMethod;
    }
    return null;
  }

  public static boolean isMainMethod(final PsiMethod method) {
    if (method == null) return false;
    if (PsiType.VOID != method.getReturnType()) return false;
    if (!method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    final PsiType type = parameters[0].getType();
    if (!(type instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    return componentType.equalsToText("java.lang.String");
  }

  public static boolean hasMainMethod(final PsiClass psiClass) {
    return findMainMethod(psiClass.findMethodsByName("main", true)) != null;
  }

  @Nullable
  public static PsiMethod findMainInClass(final PsiClass aClass) {
    if (!ConfigurationUtil.MAIN_CLASS.value(aClass)) return null;
    return findMainMethod(aClass);
  }
}
