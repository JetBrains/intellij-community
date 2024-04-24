// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.codeInsight.runner.JavaMainMethodProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PsiMethodUtil {

  public static final Condition<PsiClass> MAIN_CLASS = psiClass -> {
    if (PsiUtil.isLocalOrAnonymousClass(psiClass)) return false;
    if (psiClass.isAnnotationType()) return false;
    if (psiClass.isInterface() && !PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, psiClass)) return false;
    return psiClass.getContainingClass() == null || psiClass.hasModifierProperty(PsiModifier.STATIC);
  };

  private PsiMethodUtil() { }

  @Nullable
  public static PsiMethod findMainMethod(final PsiClass aClass) {
    List<JavaMainMethodProvider> extensionList = JavaMainMethodProvider.EP_NAME.getExtensionList();
    DumbService dumbService = DumbService.getInstance(aClass.getProject());
    for (JavaMainMethodProvider provider : dumbService.filterByDumbAwareness(extensionList)) {
      if (provider.isApplicable(aClass)) {
        return provider.findMainInClass(aClass);
      }
    }

    try {
      return dumbService.computeWithAlternativeResolveEnabled((ThrowableComputable<PsiMethod, Throwable>)() -> {
        final PsiMethod[] mainMethods = aClass.findMethodsByName("main", true);
        return findMainMethod(mainMethods, aClass);
      });
    }
    catch (IndexNotReadyException e) {
      return null;
    }
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
        if (containingClass.isInterface() && mainMethod.hasModifierProperty(PsiModifier.STATIC) && !inheritedStaticMainEnabled(containingClass)) {
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
   * ATTENTION 1: does not check the method name equals "main"<br>
   * ATTENTION 2: does not use implementations of {@link JavaMainMethodProvider}
   * (unlike {@link #hasMainMethod(PsiClass)} or {@link #findMainMethod(PsiClass)})
   *
   * @param method the method to check
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

  private static boolean isJavaLangStringArray(@NotNull PsiParameter parameter) {
    final PsiType type = parameter.getType();
    if (!(type instanceof PsiArrayType)) return false;
    try {
      return DumbService.getInstance(parameter.getProject()).computeWithAlternativeResolveEnabled(
        (ThrowableComputable<Boolean, Throwable>)() -> {
          final PsiType componentType = ((PsiArrayType)type).getComponentType();
          return componentType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
        }
      );
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  public static boolean hasMainMethod(final PsiClass psiClass) {
    DumbService dumbService = DumbService.getInstance(psiClass.getProject());
    for (JavaMainMethodProvider provider : dumbService.filterByDumbAwareness(JavaMainMethodProvider.EP_NAME.getExtensionList())) {
      if (provider.isApplicable(psiClass)) {
        return provider.hasMainMethod(psiClass);
      }
    }

    try {
      return dumbService.computeWithAlternativeResolveEnabled((ThrowableComputable<Boolean, Throwable>)() -> {
        final PsiMethod[] mainMethods = psiClass.findMethodsByName("main", true);
        return findMainMethod(mainMethods, psiClass) != null;
      });
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  @Nullable
  public static PsiMethod findMainInClass(final PsiClass aClass) {
    if (!MAIN_CLASS.value(aClass)) return null;
    return findMainMethod(aClass);
  }
}
