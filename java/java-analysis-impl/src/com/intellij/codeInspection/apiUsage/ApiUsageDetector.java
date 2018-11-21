// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.apiUsage;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

/**
 * Interface containing all types of API usages events emitted by {@link ApiUsageVisitorBase}.
 *
 * The most common event is {@link #processReference(PsiReference, boolean)},
 * which reports a reference to a class, method, field, or any other API.
 * Implicit usages of APIs, which are not present in source code by any reference, are handled by remaining methods.
 */
interface ApiUsageDetector {

  /**
   * Reference to API is found, maybe inside import statement.
   */
  void processReference(@NotNull PsiReference reference, boolean insideImport);

  /**
   * Invocation of a constructor is found in {@code new} expression.
   * {@code instantiatedClass} is a reference to a class being instantiated.
   * <br>
   * When anonymous class is instantiated, the {@code instantiatedClass} is the base class.
   */
  default void processConstructorInvocation(@NotNull PsiJavaCodeReferenceElement instantiatedClass, @NotNull PsiMethod constructor) {
    processReference(instantiatedClass, false);
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
