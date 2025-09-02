// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Allows disabling highlighting of certain elements as unused when such elements are not referenced
 * from the code but are referenced in some other way. For example,
 * <ul>
 * <li>from generated code</li>
 * <li>from outside containers: {@code @javax.servlet.annotation.WebServlet public class MyServlet {}}</li>
 * <li>from some frameworks: {@code @javax.ejb.EJB private DataStore myInjectedDataStore;}</li> etc
 * </ul>
 */
public interface ImplicitUsageProvider {
  ExtensionPointName<ImplicitUsageProvider> EP_NAME = new ExtensionPointName<>("com.intellij.implicitUsageProvider");

  /**
   * @return true if the element should not be reported as unused
   */
  boolean isImplicitUsage(@NotNull PsiElement element);

  /**
   * @return true if the element should not be reported as "assigned but not used"
   */
  boolean isImplicitRead(@NotNull PsiElement element);

  /**
   * @return true if the element should not be reported as "referenced but never assigned"
   */
  boolean isImplicitWrite(@NotNull PsiElement element);

  /**
   * @return true if the given element is implicitly initialized to a non-null value
   */
  default boolean isImplicitlyNotNullInitialized(@NotNull PsiElement element) {
    return false;
  }

  /**
   * @return true if the given element represents a class (or another data structure declaration, depending on language),
   * which instances may have implicit initialization steps not directly available in the source code.
   * <p>
   * For example, a Java class initializer which is processed via an annotation processor and custom steps are added.
   */
  default boolean isClassWithCustomizedInitialization(@NotNull PsiElement element) {
    return false;
  }
}
