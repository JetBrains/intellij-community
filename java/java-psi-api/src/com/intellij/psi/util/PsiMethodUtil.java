// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.codeInsight.runner.JavaMainMethodProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PsiMethodUtil {

  public static final Condition<@NotNull PsiClass> MAIN_CLASS = psiClass -> {
    if (PsiUtil.isLocalOrAnonymousClass(psiClass)) return false;
    if (psiClass.isAnnotationType()) return false;
    if (psiClass.isInterface() && !PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, psiClass)) return false;
    return psiClass.getContainingClass() == null || psiClass.hasModifierProperty(PsiModifier.STATIC);
  };

  private static final Comparator<PsiMethod> mainCandidateComparator = (o1, o2) -> {

    boolean isO1Static = o1.hasModifierProperty(PsiModifier.STATIC);
    boolean isO2Static = o2.hasModifierProperty(PsiModifier.STATIC);
    int o1Parameters = o1.getParameterList().getParametersCount();
    int o2Parameters = o2.getParameterList().getParametersCount();

    boolean isInheritedStaticMethodEnabled = PsiUtil.isAvailable(JavaFeature.INHERITED_STATIC_MAIN_METHOD, o1);

    if (isInheritedStaticMethodEnabled) {
      //if there are methods with 1 parameter and 0 parameters, the method with 1 parameter will be called
      //12.1.4 jep 463
      return Integer.compare(o2Parameters, o1Parameters);
    }

    //only for java 21 preview, old implementation, it should be deleted after expiring 21 preview
    if (isO1Static == isO2Static) {
      return Integer.compare(o2Parameters, o1Parameters);
    }
    else if (isO1Static) {
      return -1;
    }
    else {
      return 1;
    }
  };

  private PsiMethodUtil() { }

  public static @Nullable PsiMethod findMainMethod(@NotNull final PsiClass aClass) {
    JavaMainMethodProvider mainMethodProvider = getApplicableMainMethodProvider(aClass);
    if (mainMethodProvider != null) {
      return mainMethodProvider.findMainInClass(aClass);
    }

    return findMainMethodInClassOrParent(aClass);
  }

  /**
   * Finds the main method in the given class or its parents.
   * ATTENTION: this method does not use implementations of {@link JavaMainMethodProvider}
   * if you need to take into account custom entry points, use {@link #hasMainMethod(PsiClass)} or {@link #findMainMethod(PsiClass)}
   *
   * @param aClass the class in which to find the main method.
   * @return the main method if found, or null if not found or if an {@link IndexNotReadyException} occurs.
   */
  public static @Nullable PsiMethod findMainMethodInClassOrParent(PsiClass aClass) {
    DumbService dumbService = DumbService.getInstance(aClass.getProject());
    try {
      return dumbService.computeWithAlternativeResolveEnabled((ThrowableComputable<PsiMethod, Throwable>)() -> {
        final PsiMethod[] mainMethods = aClass.findMethodsByName("main", true);
        return findMainMethod(mainMethods, aClass, false);
      });
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  /**
   * Finds the main method in the given array of methods for a specified class, optionally returning the first match.
   *
   * @param mainMethods An array of methods to search through.
   * @param aClass The class in which to find the main method.
   * @param first If true, return the first encountered main method; otherwise, sorting them.
   * @return The main method if found, or null if not found or if it does not meet the criteria.
   */
  private static @Nullable PsiMethod findMainMethod(final PsiMethod[] mainMethods, PsiClass aClass, boolean first) {
    List<@NotNull PsiMethod> candidates = new ArrayList<>();
    //from java 22 main methods are chosen according to parameters
    boolean chooseMainMethodByParametersEnabled = inheritedStaticMainEnabled(aClass);
    for (final PsiMethod mainMethod : mainMethods) {
      if (mainMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        continue;
      }
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
          !mainMethod.hasModifierProperty(PsiModifier.STATIC) &&
          !chooseMainMethodByParametersEnabled) {
        continue;
      }
      PsiClass containingClass = mainMethod.getContainingClass();
      if (containingClass != null && containingClass != aClass) {
        if (containingClass.isInterface() && !instanceMainMethodsEnabled(containingClass)) {
          continue;
        }
        if (containingClass.isInterface() &&
            mainMethod.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
      }
      if (isMainMethod(mainMethod, false)) {
        if (first && !chooseMainMethodByParametersEnabled &&
            (mainMethod.hasModifierProperty(PsiModifier.STATIC) || hasDefaultNonPrivateConstructor(aClass))) {
          //fast exit
          return mainMethod;
        }
        candidates.add(mainMethod);
      }
    }
    if(candidates.isEmpty()) {
      return null;
    }
    candidates.sort(mainCandidateComparator);
    PsiMethod method = candidates.get(0);
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return null;
      }
      if (!hasDefaultNonPrivateConstructor(aClass)) {
        return null;
      }
    }
    return method;
  }

  private static boolean hasDefaultNonPrivateConstructor(@NotNull PsiClass clazz) {
    PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length == 0) {
      return true;
    }

    for (PsiMethod constr: constructors) {
      if ((constr.hasModifierProperty(PsiModifier.PUBLIC)
           || constr.hasModifierProperty(PsiModifier.PROTECTED)
           || constr.hasModifierProperty(PsiModifier.PACKAGE_LOCAL))
          && constr.getParameterList().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static boolean instanceMainMethodsEnabled(@NotNull PsiElement psiElement) {
    return PsiUtil.isAvailable(JavaFeature.INSTANCE_MAIN_METHOD, psiElement) && psiElement.getLanguage() instanceof JavaLanguage;
  }

  private static boolean inheritedStaticMainEnabled(@NotNull PsiElement psiElement) {
    return PsiUtil.isAvailable(JavaFeature.INHERITED_STATIC_MAIN_METHOD, psiElement);
  }

  /**
   * ATTENTION 1: does not check the method name equals "main"<br>
   * ATTENTION 2: does not use implementations of {@link JavaMainMethodProvider}
   * (unlike {@link #hasMainMethod(PsiClass)} or {@link #findMainMethod(PsiClass)})
   * ATTENTION 3: another "main" method can be launched
   *
   * @param mainMethod the method to check
   * @param checkNoArgsConstructor true if the method should be considered a main method if its class has a constructor with no parameters, false otherwise.
   * @return true, if the method satisfies a main method signature. false, otherwise
   */
  @Contract("null, _ -> false")
  private static boolean isMainMethod(final @Nullable PsiMethod mainMethod, boolean checkNoArgsConstructor) {
    if (mainMethod == null || mainMethod.getContainingClass() == null) return false;
    PsiClass containingClass = mainMethod.getContainingClass();
    if (containingClass == null) return false;
    if (!PsiTypes.voidType().equals(mainMethod.getReturnType())) return false;
    final PsiParameter[] parameters = mainMethod.getParameterList().getParameters();
    if (instanceMainMethodsEnabled(mainMethod)) {
      if (!mainMethod.hasModifierProperty(PsiModifier.PUBLIC) &&
          !mainMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
          !mainMethod.hasModifierProperty(PsiModifier.PROTECTED)) return false;
      //can't instantiate this class
      if (containingClass.getContainingClass() != null && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      if (checkNoArgsConstructor && !mainMethod.hasModifierProperty(PsiModifier.STATIC) &&  !hasDefaultNonPrivateConstructor(containingClass)) {
        return false;
      }
      if (parameters.length == 1) {
        return isJavaLangStringArray(parameters[0]);
      }
      return parameters.length == 0;
    } else {
      if (!mainMethod.hasModifierProperty(PsiModifier.STATIC)) return false;
      if (!mainMethod.hasModifierProperty(PsiModifier.PUBLIC)) return false;
      if (parameters.length != 1) return false;
      return isJavaLangStringArray(parameters[0]);
    }
  }

  /**
   * ATTENTION 1: does not check the method name equals "main"<br>
   * ATTENTION 2: does not use implementations of {@link JavaMainMethodProvider}
   * (unlike {@link #hasMainMethod(PsiClass)} or {@link #findMainMethod(PsiClass)})
   * ATTENTION 3: another "main" method can be launched
   * @param method the method to check
   * @return true, if the method satisfies a main method signature. false, otherwise
   */
  @Contract("null -> false")
  public static boolean isMainMethod(final @Nullable PsiMethod method) {
    return isMainMethod(method, true);
  }

  private static boolean isJavaLangStringArray(@NotNull PsiParameter parameter) {
    try {
      final PsiType type = parameter.getType();
      if (!(type instanceof PsiArrayType)) return false;

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
    JavaMainMethodProvider mainMethodProvider = getApplicableMainMethodProvider(psiClass);
    if (mainMethodProvider != null) {
      return mainMethodProvider.hasMainMethod(psiClass);
    }

    DumbService dumbService = DumbService.getInstance(psiClass.getProject());
    try {
      return dumbService.computeWithAlternativeResolveEnabled((ThrowableComputable<Boolean, Throwable>)() -> {
        final PsiMethod[] mainMethods = psiClass.findMethodsByName("main", true);
        return findMainMethod(mainMethods, psiClass, true) != null;
      });
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  public static boolean isMainMethodWithProvider(@NotNull PsiClass psiClass, @NotNull PsiElement psiElement) {
    JavaMainMethodProvider mainMethodProvider = getApplicableMainMethodProvider(psiClass);
    if (mainMethodProvider != null) {
      return mainMethodProvider.isMain(psiElement);
    }

    DumbService dumbService = DumbService.getInstance(psiElement.getProject());
    try {
      return dumbService.computeWithAlternativeResolveEnabled((ThrowableComputable<Boolean, Throwable>)() -> {
        PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
        if (psiMethod == null) return false;

        return "main".equals(psiMethod.getName()) && isMainMethod(psiMethod);
      });
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  public static @Nullable String getMainJVMClassName(@NotNull PsiClass psiClass) {
    JavaMainMethodProvider mainMethodProvider = getApplicableMainMethodProvider(psiClass);
    if (mainMethodProvider != null) {
      return mainMethodProvider.getMainClassName(psiClass);
    }

    return ClassUtil.getJVMClassName(psiClass);
  }

  public static @Nullable PsiMethod findMainInClass(final @NotNull PsiClass aClass) {
    if (!MAIN_CLASS.value(aClass)) return null;
    return findMainMethod(aClass);
  }

  /**
   * Determines if the given class has a main method and can be launched.
   *
   * @param aClass the class to check for a main method.
   * @return true if the class has a main method, false otherwise.
   */
  public static boolean hasMainInClass(final @NotNull PsiClass aClass) {
    if (!MAIN_CLASS.value(aClass)) return false;
    return hasMainMethod(aClass);
  }

  private static @Nullable JavaMainMethodProvider getApplicableMainMethodProvider(@NotNull PsiClass aClass) {
    DumbService dumbService = DumbService.getInstance(aClass.getProject());

    List<JavaMainMethodProvider> javaMainMethodProviders =
      dumbService.filterByDumbAwareness(JavaMainMethodProvider.EP_NAME.getExtensionList());

    return ContainerUtil.find(javaMainMethodProviders, provider -> provider.isApplicable(aClass));
  }
}
