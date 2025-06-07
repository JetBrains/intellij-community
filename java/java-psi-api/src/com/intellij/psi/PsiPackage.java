// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmPackage;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

/**
 * Represents a Java package.
 */
public interface PsiPackage extends PsiCheckedRenameElement, NavigationItem, PsiJvmModifiersOwner,
                                    PsiDirectoryContainer, PsiQualifiedNamedElement, JvmPackage {

  String PACKAGE_INFO_CLASS = "package-info";
  String PACKAGE_INFO_FILE = PACKAGE_INFO_CLASS + ".java";
  String PACKAGE_INFO_CLS_FILE = PACKAGE_INFO_CLASS + ".class";

  PsiPackage[] EMPTY_ARRAY = new PsiPackage[0];

  /**
   * Returns the full-qualified name of the package.
   *
   * @return the full-qualified name, or an empty string for the default package.
   */
  @Override
  @NotNull
  @NlsSafe
  String getQualifiedName();

  /**
   * Returns the parent of the package.
   *
   * @return the parent package, or null for the default package.
   */
  @Override
  @Nullable
  PsiPackage getParentPackage();

  /**
   * Returns the list of subpackages of this package under all source roots of the project.
   *
   * @return the array of subpackages.
   */
  PsiPackage @NotNull [] getSubPackages();

  /**
   * Returns the list of subpackages of this package in the specified search scope.
   *
   * @param scope the scope in which packages are searched.
   * @return the array of subpackages.
   */
  PsiPackage @NotNull [] getSubPackages(@NotNull GlobalSearchScope scope);

  /**
   * Returns the list of classes in all directories corresponding to the package.
   *
   * @return the array of classes.
   */
  PsiClass @NotNull [] getClasses();

  /**
   * Returns the list of classes in directories corresponding to the package in the specified
   * search scope.
   *
   * @param scope the scope in which directories are searched.
   * @return the array of classes.
   */
  PsiClass @NotNull [] getClasses(@NotNull GlobalSearchScope scope);

  /**
   * @param scope scope to limit the query to
   * @return list of individual files declared as single file source roots that belong to this package and the supplied scope.
   */
  @ApiStatus.Experimental
  default @NotNull @Unmodifiable Collection<@NotNull PsiFile> getIndividualFiles(@NotNull GlobalSearchScope scope) {
    return Collections.emptyList();
  }

  /**
   * Returns the list of all files in the package, restricted by the specified scope. (This is
   * normally the list of all files in all directories corresponding to the package, but it can
   * be modified by custom language plugins which have a different notion of packages.)
   */
  PsiFile @NotNull [] getFiles(@NotNull GlobalSearchScope scope);

  /**
   * Returns the list of package-level annotations for the package.
   *
   * @return the list of annotations, or null if the package does not have any package-level annotations.
   */
  @Nullable
  PsiModifierList getAnnotationList();

  /**
   * This method must be invoked on the package after all directories corresponding
   * to it have been renamed/moved accordingly to qualified name change.
   *
   * @param newQualifiedName the new qualified name of the package.
   */
  void handleQualifiedNameChange(@NotNull String newQualifiedName);

  /**
   * Returns source roots that this package occurs in package prefixes of.
   *
   * @return the array of virtual files for the source roots.
   */
  VirtualFile @NotNull [] occursInPackagePrefixes();

  @Override
  @Nullable("default package") @NlsSafe
  String getName();

  boolean containsClassNamed(@NotNull String name);

  PsiClass @NotNull [] findClassByShortName(@NotNull String name, @NotNull GlobalSearchScope scope);

  /**
   * Returns {@code true} if the package contains a class with short name {@code name} belonging to the provided scope.
   */
  default boolean hasClassWithShortName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    return findClassByShortName(name, scope).length > 0;
  }
}