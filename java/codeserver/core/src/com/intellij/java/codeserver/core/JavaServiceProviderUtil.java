// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Utility for working with Java services (jigsaw)
 */
public final class JavaServiceProviderUtil {
  public static final String PROVIDER = "provider";
  public static final Set<String> JAVA_UTIL_SERVICE_LOADER_METHODS = Set.of("load", "loadInstalled");

  /**
   * Checks if the given method is a service provider method.
   *
   * @param method for checking
   * @return true if the method is a service provider method, false otherwise
   */
  public static boolean isServiceProviderMethod(@NotNull PsiMethod method) {
    return PROVIDER.equals(method.getName()) &&
           method.getParameterList().isEmpty() &&
           method.hasModifierProperty(PsiModifier.PUBLIC) &&
           method.hasModifierProperty(PsiModifier.STATIC);
  }

  /**
   * Finds a service provider method within a given PsiClass.
   *
   * @param psiClass to search for the service provider method
   * @return service provider method, or null if not found
   */
  public static @Nullable PsiMethod findServiceProviderMethod(@NotNull PsiClass psiClass) {
    return ContainerUtil.find(psiClass.findMethodsByName("provider", false), JavaServiceProviderUtil::isServiceProviderMethod);
  }
}
