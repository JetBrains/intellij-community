/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.pom.java.LanguageLevel;

/**
 * Represents a Java, JSP or class file.
 */
public interface PsiJavaFile extends PsiImportHolder, PsiClassOwner {
  /**
   * Returns the package statement contained in the file.
   *
   * @return the package statement, or null if one is missing.
   */
  @Nullable PsiPackageStatement getPackageStatement();

  /**
   * Returns the name of the package to which the file belongs.
   *
   * @return the name specified in the package statement, or an empty string for a JSP page or
   * file which has no package statement.
   */
  @Override
  @NotNull String getPackageName();

  /**
   * Returns the import list contained in the file.
   *
   * @return the import list, or null if this element represents a compiled class.
   */
  @Nullable PsiImportList getImportList();

  /**
   * Returns the list of classes or packages which have been
   * imported on demand (for example, javax.swing.*)
   *
   * @param includeImplicit if true, implicitly imported packages (like java.lang) are included.
   * @param checkIncludes   deprecated, no longer used
   * @return the list of PsiClass or PsiPackage elements for the imports.
   */
  @NotNull PsiElement[] getOnDemandImports(boolean includeImplicit, @Deprecated boolean checkIncludes);

  /**
   * Returns the list of classes which have been imported as
   * single-class imports.
   *
   * @param checkIncludes deprecated, no longer used.
   * @return the list of PsiClass elements for the import.
   */
  @NotNull PsiClass[] getSingleClassImports(@Deprecated boolean checkIncludes);

  /**
   * Returns the list of names of implicitly imported packages
   * (for example, java.lang).
   *
   * @return the list of implicitly imported package names.
   */
  @NotNull String[] getImplicitlyImportedPackages();

  /**
   * returns the list of reference elements for the
   * implicitly imported packages (for example, java.lang).
   *
   * @return the list of implicitly imported package reference elements.
   */
  @NotNull PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences();

  /**
   * Returns the single-class import statement which references
   * the specified class, or null if there is no such statement.
   *
   * @param aClass the class to return the import statement for.
   * @return the Java code reference under the import statement, or null if there is no such statement.
   */
  @Nullable PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass);

  @NotNull LanguageLevel getLanguageLevel();
}
