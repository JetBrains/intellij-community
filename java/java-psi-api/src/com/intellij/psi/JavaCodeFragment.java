// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a fragment of Java code which exists outside of a project structure (for example,
 * in a foreign language code or in a user interface element other than the main source code editor).
 */
public interface JavaCodeFragment extends PsiImportHolder, PsiCodeFragment {
  /**
   * Returns the type corresponding to the {@code this} keyword in the code fragment.
   *
   * @return the type of {@code this} in the fragment.
   */
  PsiType getThisType();

  /**
   * Sets the type corresponding to the {@code this} keyword in the code fragment.
   *
   * @param psiType the type of {@code this} in the fragment.
   */
  void setThisType(PsiType psiType);

  /**
   * Returns the type corresponding to the {@code super} keyword in the code fragment.
   *
   * @return the type of {@code super} in the fragment.
   */
  PsiType getSuperType();

  /**
   * Sets the type corresponding to the {@code super} keyword in the code fragment.
   *
   * @param superType the type of {@code super} in the fragment.
   */
  void setSuperType(PsiType superType);

  /**
   * Return the fully-qualified package name this fragment is located in,
   * or {@code null} if it was not specified when this fragment was created.
   * 
   * @return the fully-qualified package name if it was specified, {@code null} otherwise.
   */
  default @Nullable String getPackageName() {
    return null;
  }

  /**
   * Returns the list of classes considered to be imported by the code fragment.
   *
   * @return the comma-separated list of full-qualified names of classes considered
   * to be imported.
   */
  String importsToString();

  /**
   * Adds the specified classes to the list of classes considered to be imported by the
   * code fragment.
   *
   * @param imports the comma-separated list of full-qualified names of classes to import.
   */
  void addImportsFromString(String imports);

  /**
   * Sets the visibility checker which is used to determine the visibility of declarations
   * from the code fragment.
   *
   * @param checker the checker instance.
   */
  void setVisibilityChecker(VisibilityChecker checker);

  /**
   * Gets the visibility checker which is used to determine the visibility of declarations
   * from the code fragment.
   *
   * @return the checker instance.
   */
  VisibilityChecker getVisibilityChecker();

  /**
   * Sets the exception handler which is used to determine which exceptions are considered handled
   * in the context where the fragment is used.
   *
   * @param checker the exception handler instance.
   */
  void setExceptionHandler(ExceptionHandler checker);

  /**
   * Gets the exception handler which is used to determine which exceptions are considered handled
   * in the context where the fragment is used.
   *
   * @return the exception handler instance.
   */
  ExceptionHandler getExceptionHandler();

  /**
   * Interface used to determine the visibility of declarations from the code fragment.
   */
  interface VisibilityChecker {
    /**
     * Returns the visibility of the specified declaration from the specified location.
     *
     * @param declaration the referenced declaration.
     * @param place the location of the reference to the declaration.
     * @return the visibility of the declaration.
     */
    Visibility isDeclarationVisible(PsiElement declaration, PsiElement place);

    enum Visibility {
      /**
       * The declaration is visible from the location.
       */
      VISIBLE,

      /**
       * The declaration is not visible from the location.
       */
      NOT_VISIBLE,

      /**
       * The visibility of the declaration from the location is defined by Java scoping rules.
       */
      DEFAULT_VISIBILITY
    }

    /**
     * The visibility checker which reports all declarations as visible.
     */
    VisibilityChecker EVERYTHING_VISIBLE = new VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        return Visibility.VISIBLE;
      }

      @Override
      public String toString() {
        return "EVERYTHING_VISIBLE";
      }
    };

    VisibilityChecker PROJECT_SCOPE_VISIBLE = new VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        return declaration.getManager().isInProject(declaration) ? Visibility.VISIBLE : Visibility.NOT_VISIBLE;
      }

      @Override
      public String toString() {
        return "PROJECT_SCOPE_VISIBLE";
      }
    };
  }

  /**
   * Interface used to determine which exceptions are considered handled
   * in the context where the fragment is used.
   */
  interface ExceptionHandler {
    /**
     * Checks if the specified exception is considered handled
     * in the context where the fragment is used.
     *
     * @param exceptionType the type of the exception to check.
     * @return true if the exception is handled, false otherwise.
     */
    boolean isHandledException(PsiClassType exceptionType);
  }
}
