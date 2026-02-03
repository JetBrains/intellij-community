// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class JavaPsiFacade {
  public static JavaPsiFacade getInstance(@NotNull Project project) {
    return project.getService(JavaPsiFacade.class);
  }

  public static PsiElementFactory getElementFactory(@NotNull Project project) {
    return getInstance(project).getElementFactory();
  }

  /**
   * Searches the specified scope within the project for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @return the PSI class, or {@code null} if no class with such name is found.
   */
  public abstract @Nullable PsiClass findClass(@NonNls @NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Searches the specified scope within the project for classes with the specified full-qualified
   * name and returns all found classes.
   *
   * @return the array of found classes, or an empty array if no classes are found.
   */
  public abstract PsiClass @NotNull [] findClasses(@NonNls @NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Returns {@code true} if the specified scope contains the class with the specified fully qualified name, and {@code false} otherwise.
   * This method is equivalent to {@code findClass(...) != null} and {@code findClasses(...).length > 0} checks, but in big projects it
   * may work much faster, because it doesn't need to sort entries if multiple classes with the specified name are present in the scope.
   */
  public abstract boolean hasClass(@NonNls @NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Searches the project for the package with the specified full-qualified name and returns one
   * if it is found.
   *
   * @return the PSI package, or {@code null} if no package with such name is found.
   */
  public abstract @Nullable PsiPackage findPackage(@NonNls @NotNull String qualifiedName);

  /**
   * Searches the scope for a unique Java module with the given name.
   */
  public abstract @Nullable PsiJavaModule findModule(@NotNull String moduleName, @NotNull GlobalSearchScope scope);

  /**
   * Searches the scope for Java modules with the given name.
   * In dumb mode this method returns an empty list.
   * Supports DumbModeAccessType, in this case the values are not cached
   */
  public abstract @NotNull Collection<PsiJavaModule> findModules(@NotNull String moduleName, @NotNull GlobalSearchScope scope);

  /**
   * Returns the element factory for the project, which can be used to
   * create instances of Java elements.
   */
  public abstract @NotNull PsiElementFactory getElementFactory();

  /**
   * Returns the factory for the project, which can be used to create instances of certain Java constructs from their textual
   * representation. Elements created shall not be used to later intermix (like insert into) a PSI parsed from the user codebase
   * since no formatting to the user code style will be performed in this case. Please use {@link #getElementFactory()} instead, which
   * provides exactly same methods but ensures created instances will get properly formatted.
   *
   * @return the parser facade.
   */
  public abstract @NotNull PsiJavaParserFacade getParserFacade();

  /**
   * Returns the resolve helper for the project, which can be used to resolve references
   * and check accessibility of elements.
   */
  public abstract @NotNull PsiResolveHelper getResolveHelper();

  /**
   * Returns the name helper for the project, which can be used to validate
   * and parse Java identifiers.
   *
   * @deprecated use {@link PsiNameHelper#getInstance(Project)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public abstract @NotNull PsiNameHelper getNameHelper();

  /**
   * Returns the constant expression evaluator for the project.
   *
   * @return the evaluator instance.
   */
  public abstract @NotNull PsiConstantEvaluationHelper getConstantEvaluationHelper();

  /**
   * Checks if the specified package name is part of the package prefix for
   * any of the modules in this project.
   */
  public abstract boolean isPartOfPackagePrefix(@NotNull String packageName);

  /**
   * Checks if the specified PSI element belongs to the specified package.
   */
  public abstract boolean isInPackage(@NotNull PsiElement element, @NotNull PsiPackage aPackage);

  /**
   * Checks if the specified PSI elements belong to the same package.
   */
  public abstract boolean arePackagesTheSame(@NotNull PsiElement element1, @NotNull PsiElement element2);

  public abstract @NotNull Project getProject();

  public abstract boolean isConstantExpression(@NotNull PsiExpression expression);
}