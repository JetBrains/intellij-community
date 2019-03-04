// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.apiUsage;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Interface containing all types of API usages events emitted by {@link ApiUsageVisitorBase}.
 *
 * The most common event is {@link #processReference(PsiReference)},
 * which reports a reference to a class, method, field, or any other API.
 * Implicit usages of APIs, which are not present in source code by any reference, are handled by remaining methods.
 */
interface ApiUsageDetector {

  /**
   * Checks whether references from this PSI element to other PSI elements must be processed.
   * <br>
   * For example, it may be overridden to ignore references of elements residing in import statements.
   */
  default boolean shouldProcessReferences(@NotNull PsiElement element) {
    return true;
  }

  /**
   * Reference to API is found.
   */
  void processReference(@NotNull PsiReference reference);

  /**
   * Invocation of a constructor is found in {@code new} expression.
   * {@code instantiatedClass} is a reference to a class being instantiated.
   * <br>
   * When anonymous class is instantiated, the {@code instantiatedClass} is the base class.
   */
  default void processConstructorInvocation(@NotNull PsiJavaCodeReferenceElement instantiatedClass, @NotNull PsiMethod constructor) {
    processReference(instantiatedClass);
  }

  /**
   * Invocation of the default constructor of a class is found in a {@code new} expression.
   * <pre>{@code
   * class Test {
   *   //The default implicit constructor
   * }
   *
   * void foo() {
   *   Object o = new Test(); //The default constructor invocation
   * }}</pre>
   * {@code instantiatedClass} is a reference to a class being instantiated.
   * <br>
   * When anonymous class is instantiated, the {@code instantiatedClass} is the base class.
   */
  default void processDefaultConstructorInvocation(@NotNull PsiJavaCodeReferenceElement instantiatedClass) {}

  /**
   * Invocation of the empty constructor of a super class is found in the default constructor of a subclass, which is not an anonymous class.
   * <pre>
   * {@code
   * class Subclass extends Super {
   *   <implicit invocation of Super() in default constructor>
   * }}
   * </pre>
   */
  default void processEmptyConstructorOfSuperClassImplicitInvocationAtSubclassDeclaration(
    @NotNull PsiClass subclass,
    @NotNull PsiClass superClass
  ) { }

  /**
   * Implicit invocation of an empty constructor of a super class is found in a constructor of a subclass.
   * The empty constructor is either a constructor with no arguments, or the default constructor.
   *
   * <pre>
   * {@code
   * class Subclass extends Super {
   *    Subclass() {
   *       <implicit invocation of Super()>
   *    }
   * }}
   * </pre>
   */
  default void processEmptyConstructorOfSuperClassImplicitInvocationAtSubclassConstructor(
    @NotNull PsiClass superClass,
    @NotNull PsiMethod subclassConstructor
  ) {}

  /**
   * Method of a super class {@code overriddenMethod} is overridden by a subclass' {@code method}.
   *
   * The super class is {@code overriddenMethod.containingClass}, and the subclass is {@code method.containingClass}.
   */
  default void processMethodOverriding(@NotNull PsiMethod method, @NotNull PsiMethod overriddenMethod) {}

}
