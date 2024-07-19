// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the list of import statements contained in a Java or JSP file.
 *
 * @see PsiJavaFile#getImportList()
 */
public interface PsiImportList extends PsiElement {
  PsiImportList[] EMPTY_ARRAY = new PsiImportList[0];
  ArrayFactory<PsiImportList> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiImportList[count];

  /**
   * Returns the non-static import statements contained in the list.
   *
   * @return the array of non-static import statements.
   */
  PsiImportStatement @NotNull [] getImportStatements();

  /**
   * Returns the static import statements contained in the list.
   *
   * @return the array of static import statements.
   */
  PsiImportStaticStatement @NotNull [] getImportStaticStatements();

  /**
   * Returns the import module statements contained in the list.
   *
   * @return the array of import module statements.
   */
  PsiImportModuleStatement @NotNull [] getImportModuleStatements();

  /**
   * Returns all import statements contained in the list.
   *
   * @return the array of import statements.
   */
  PsiImportStatementBase @NotNull [] getAllImportStatements();

  /**
   * Searches the list for a single-class import statement importing the specified class.
   *
   * @param qName the full-qualified name of the imported class.
   * @return the import statement, or null if one was not found.
   */
  @Nullable
  PsiImportStatement findSingleClassImportStatement(String qName);

  /**
   * Searches the list for an on-demand import statement importing the specified class.
   *
   * @param packageName the name of the imported package.
   * @return the import statement, or null if one was not found.
   */
  @Nullable
  PsiImportStatement findOnDemandImportStatement(@NonNls String packageName);

  /**
   * Searches the list for a module import statement importing the specified class.
   *
   * @param moduleName the name of the imported module.
   * @return the import module statement, or null if one was not found.
   */
  @Nullable
  PsiImportModuleStatement findImportModuleStatement(@NonNls String moduleName);

  /**
   * Searches the list for a single import or import static statement importing the specified
   * identifier.
   *
   * @param name the name of the imported class or method.
   * @return the import statement, or null if one was not found.
   */
  @Nullable
  PsiImportStatementBase findSingleImportStatement(String name);

  /**
   * Checks if replacing this import list with the specified import list will cause no
   * modifications to the file.
   *
   * @param otherList the list to check possibility to replace with.
   * @return true if replacing will not cause modifications; false otherwise.
   */
  boolean isReplaceEquivalent(PsiImportList otherList);
}
