// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.codeInsight.runner.JavaMainMethodProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiMethodUtil {

  public static final Condition<PsiClass> MAIN_CLASS = psiClass -> {
    if (PsiUtil.isLocalOrAnonymousClass(psiClass)) return false;
    if (psiClass.isAnnotationType()) return false;
    if (psiClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(psiClass)) return false;
    return psiClass.getContainingClass() == null || psiClass.hasModifierProperty(PsiModifier.STATIC);
  };

  private PsiMethodUtil() { }

  @Nullable
  public static PsiMethod findMainMethod(final PsiClass aClass) {
    for (JavaMainMethodProvider provider : JavaMainMethodProvider.EP_NAME.getExtensionList()) {
      if (provider.isApplicable(aClass)) {
        return provider.findMainInClass(aClass);
      }
    }
    final PsiMethod[] mainMethods = aClass.findMethodsByName("main", true);
    return findMainMethod(mainMethods, aClass);
  }

  @Nullable
  private static PsiMethod findMainMethod(final PsiMethod[] mainMethods, PsiClass aClass) {
    for (final PsiMethod mainMethod : mainMethods) {
      if (mainMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        continue;
      }
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !mainMethod.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      PsiClass containingClass = mainMethod.getContainingClass();
      if (containingClass != null && containingClass != aClass) {
        if (containingClass.isInterface() && !instanceMainMethodsEnabled(containingClass)) {
          continue;
        }
        if (mainMethod.hasModifierProperty(PsiModifier.STATIC) && !inheritedStaticMainEnabled(containingClass)) {
          continue;
        }
      }
      if (isMainMethod(mainMethod)) return mainMethod;
    }
    return null;
  }

  private static boolean instanceMainMethodsEnabled(@NotNull PsiElement psiElement) {
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(psiElement);
    boolean is21Preview = languageLevel.equals(LanguageLevel.JDK_21_PREVIEW);
    boolean is22PreviewOrOlder = languageLevel.isAtLeast(LanguageLevel.JDK_22_PREVIEW);
    return is21Preview || is22PreviewOrOlder;
  }

  private static boolean inheritedStaticMainEnabled(@NotNull PsiElement psiElement) {
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(psiElement);
    return languageLevel.isAtLeast(LanguageLevel.JDK_22_PREVIEW);
  }

  /**
   * ATTENTION: does not check the method name equals "main"
   *
   * @param method  the method to check
   * @return true, if the method satisfies a main method signature. false, otherwise
   */
  public static boolean isMainMethod(final PsiMethod method) {
    if (method == null || method.getContainingClass() == null) return false;
    if (!PsiTypes.voidType().equals(method.getReturnType())) return false;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (instanceMainMethodsEnabled(method)) {
      if (!method.hasModifierProperty(PsiModifier.PUBLIC) &&
          !method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
          !method.hasModifierProperty(PsiModifier.PROTECTED)) return false;
      PsiMethod[] constructors = method.getContainingClass().getConstructors();
      if (!method.hasModifierProperty(PsiModifier.STATIC) && constructors.length != 0 && !ContainerUtil.exists(constructors, method1 -> method1.getParameterList().isEmpty())) {
        return false;
      }
      if (parameters.length == 1) {
        return isJavaLangStringArray(parameters[0]);
      }
      return parameters.length == 0;
    } else {
      if (!method.hasModifierProperty(PsiModifier.STATIC)) return false;
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return false;
      if (parameters.length != 1) return false;
      return isJavaLangStringArray(parameters[0]);
    }
  }

  private static boolean isJavaLangStringArray(PsiParameter parameter) {
    final PsiType type = parameter.getType();
    if (!(type instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    return componentType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }

  public static boolean hasMainMethod(final PsiClass psiClass) {
    for (JavaMainMethodProvider provider : JavaMainMethodProvider.EP_NAME.getExtensionList()) {
      if (provider.isApplicable(psiClass)) {
        return provider.hasMainMethod(psiClass);
      }
    }
    return findMainMethod(psiClass.findMethodsByName("main", true), psiClass) != null;
  }

  @Nullable
  public static PsiMethod findMainInClass(final PsiClass aClass) {
    if (!MAIN_CLASS.value(aClass)) return null;
    return findMainMethod(aClass);
  }
}
