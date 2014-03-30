/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  ArrayFactory<PsiImportList> ARRAY_FACTORY = new ArrayFactory<PsiImportList>() {
    @NotNull
    @Override
    public PsiImportList[] create(int count) {
      return count == 0 ? EMPTY_ARRAY : new PsiImportList[count];
    }
  };

  /**
   * Returns the non-static import statements contained in the list.
   *
   * @return the array of non-static import statements.
   */
  @NotNull PsiImportStatement[] getImportStatements();

  /**
   * Returns the static import statements contained in the list.
   *
   * @return the array of static import statements.
   */
  @NotNull PsiImportStaticStatement[] getImportStaticStatements();

  /**
   * Returns all import statements contained in the list.
   *
   * @return the array of import statements.
   */
  @NotNull PsiImportStatementBase[] getAllImportStatements();

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
