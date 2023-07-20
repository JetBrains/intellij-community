// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.apiUsage;

import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

/**
 * Processes usages of APIs in source code of UAST-supporting languages, which are detected by {@link ApiUsageUastVisitor}.
 */
@ApiStatus.Experimental
public interface ApiUsageProcessor {

  /**
   * Process reference to a class, method (not constructor), field or any other API element found in source code.
   *
   * @param sourceNode can be used to get actual PSI element to highlight in inspections via {@code sourceNode.sourcePsi}
   * @param target     resolved API element
   * @param qualifier  is optionally a qualified expression of the reference.
   */
  default void processReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target, @Nullable UExpression qualifier) { }

  /**
   * Process implicit reference from lambda to a class which would be found in the bytecode:
   * <pre>
   * class I {
   *   static void f(Runnable r) {}
   * }
   *
   * // target represents Runnable interface
   * f(() -> println());
   * </pre>
   *
   * @param sourceNode can be used to get actual PSI element to highlight in inspections via {@code sourceNode.sourcePsi}
   * @param target     resolved API element
   */
  default void processLambda(@NotNull ULambdaExpression sourceNode, @NotNull PsiModifierListOwner target) { }

  /**
   * Process reference to an imported API element.
   *
   * @param sourceNode can be used to get actual PSI element to highlight in inspections via {@code sourceNode.sourcePsi}
   * @param target     resolved API element being imported
   */
  default void processImportReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target) { }

  /**
   * Process constructor invocation of a class.<br>
   * The invoked constructor may be the default constructor, which is not declared in source code.<br>
   * The constructor invocation may be implicit: in declaration of a subclass with no constructors defined,
   * in declaration of an anonymous class, as implicit {@code super()} invocation in subclass' constructor, etc.
   *
   * @param sourceNode          can be used to get actual PSI element to highlight in inspections via {@code sourceNode.sourcePsi}
   * @param instantiatedClass   class being instantiated
   * @param constructor         PSI constructor defined in source code, or {@code null} if the default constructor is being invoked.
   * @param subclassDeclaration declaration of a subclass or anonymous subclass where the constructor invocation's happens,
   *                            or {@code null} if the constructor is being invoked explicitly.
   */
  default void processConstructorInvocation(@NotNull UElement sourceNode,
                                            @NotNull PsiClass instantiatedClass,
                                            @Nullable PsiMethod constructor,
                                            @Nullable UClass subclassDeclaration) {}

  /**
   * Process overriding of a super class' method.
   *
   * @param method           method that overrides the parent's method. {@code method.uastAnchor.sourcePsi} can be used to highlight name declaration.
   * @param overriddenMethod super class' method being overridden
   */
  default void processMethodOverriding(@NotNull UMethod method, @NotNull PsiMethod overriddenMethod) {}

  /**
   * Process reference to a Java module found in {@code module-info.java} file.
   *
   * @param javaModuleReference Java module reference
   * @param target              resolved Java module
   */
  default void processJavaModuleReference(@NotNull PsiJavaModuleReference javaModuleReference, @NotNull PsiJavaModule target) {}
}
