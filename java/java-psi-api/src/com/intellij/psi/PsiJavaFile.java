// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java, JSP or .class file.
 */
public interface PsiJavaFile extends PsiImportHolder, PsiClassOwner, AbstractBasicJavaFile {
  /**
   * Returns the package statement contained in the file.
   *
   * @return the package statement, or null if one is missing.
   */
  @Nullable
  PsiPackageStatement getPackageStatement();

  /**
   * Returns the name of the package to which the file belongs.
   *
   * @return the name specified in the package statement, or an empty string for a JSP page or
   * file which has no package statement.
   */
  @Override
  @NotNull
  String getPackageName();

  /**
   * Returns the import list contained in the file.
   *
   * @return the import list, or null if this element represents a compiled class.
   */
  @Nullable
  PsiImportList getImportList();

  /**
   * Returns the array of classes or packages which have been
   * imported on non-static demand (for example, javax.swing.*)
   *
   * @param includeImplicit if true, implicitly imported packages (like java.lang) are included.
   * @param checkIncludes   deprecated, no longer used
   * @return the array of PsiClass or PsiPackage elements for the imports.
   * @deprecated please use other methods to check imports, the method doesn't support module imports
   */
  @Deprecated
  PsiElement @NotNull [] getOnDemandImports(boolean includeImplicit, @Deprecated boolean checkIncludes);

  /**
   * Returns the array of classes which have been imported as
   * single-class imports.
   *
   * @param checkIncludes deprecated, no longer used.
   * @return the array of PsiClass elements for the import.
   */
  PsiClass @NotNull [] getSingleClassImports(@Deprecated boolean checkIncludes);

  /**
   * Returns the array of names of implicitly imported packages
   * (for example, java.lang).
   *
   * @return the array of implicitly imported package names.
   */
  String @NotNull [] getImplicitlyImportedPackages();

  /**
   * returns the array of reference elements for the
   * implicitly imported packages (for example, java.lang).
   *
   * @return the array of implicitly imported package reference elements.
   * @deprecated Use {@link PsiJavaFile#getImplicitlyImportedPackages()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  PsiJavaCodeReferenceElement @NotNull [] getImplicitlyImportedPackageReferences();

  /**
   * Returns the single-class import statement which references
   * the specified class, or null if there is no such statement.
   *
   * @param aClass the class to return the import statement for.
   * @return the Java code reference under the import statement, or null if there is no such statement.
   */
  @Nullable
  PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass);

  @NotNull
  LanguageLevel getLanguageLevel();

  /**
   * Returns a Java module declaration element, or {@code null} if the file is not a module-info one.
   */
  @Nullable
  PsiJavaModule getModuleDeclaration();

  /**
   * @return the array of implicitly imported elements.
   * This array doesn't include elements from {@link #getImplicitlyImportedPackages()}
   */
  @ApiStatus.Experimental
  default @NotNull ImplicitlyImportedElement @NotNull [] getImplicitlyImportedElements() {
    return ImplicitlyImportedElement.EMPTY_ARRAY;
  }
}