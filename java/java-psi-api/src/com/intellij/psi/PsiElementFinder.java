// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Predicates;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Allows to extend the mechanism of locating classes and packages by full-qualified name.
 * Implementations of this interface need to be registered as extensions in order
 * to be picked up by {@link JavaPsiFacade}.
 */
public abstract class PsiElementFinder implements PossiblyDumbAware {
  public static final ProjectExtensionPointName<PsiElementFinder> EP = new ProjectExtensionPointName<>("com.intellij.java.elementFinder");

  /**
   * Searches the specified scope within the project for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope the scope to search.
   * @return the PSI class, or null if no class with such name is found.
   * @see JavaPsiFacade#findClass(String, GlobalSearchScope)
   */
  public abstract @Nullable PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Searches the specified scope within the project for classes with the specified full-qualified
   * name and returns all found classes.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope the scope to search.
   * @return the array of found classes, or an empty array if no classes are found.
   * @see JavaPsiFacade#findClasses(String, GlobalSearchScope)
   */
  public abstract PsiClass @NotNull [] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Returns {@code true} if the specified scope contains the class with the specified fully qualified name satisfying the provided filter, 
   * and {@code false} otherwise.
   */
  public boolean hasClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope, @NotNull Predicate<PsiClass> filter) {
    PsiClass[] classes = findClasses(qualifiedName, scope);
    if (filter == Predicates.<PsiClass>alwaysTrue()) return classes.length > 0;
    for (PsiClass aClass : classes) {
      if (filter.test(aClass)) return true;
    }
    return false;
  }

  /**
   * Searches the project for the package with the specified full-qualified name and returns one
   * if it is found.
   *
   * @param qualifiedName the full-qualified name of the package to find.
   * @return the PSI package, or null if no package with such name is found.
   * @see JavaPsiFacade#findPackage(String)
   */
  public @Nullable PsiPackage findPackage(@NotNull String qualifiedName) {
    return null;
  }

  /**
   * Returns the array of subpackages of the specified package in the specified search scope.
   *
   * @param psiPackage the package to return the list of subpackages for.
   * @param scope the scope in which subpackages are searched.
   * @return the array of subpackages.
   * @see PsiPackage#getSubPackages(GlobalSearchScope)
   */
  public PsiPackage @NotNull [] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return PsiPackage.EMPTY_ARRAY;
  }

  /**
   * Returns the array of classes in the specified package and in the specified search scope.
   *
   * @param psiPackage the package to return the list of classes in.
   * @param scope the scope in which classes are searched.
   * @return the array of classes.
   * @see PsiPackage#getClasses(GlobalSearchScope)
   */
  public PsiClass @NotNull [] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  /**
   * Returns the filter to exclude classes, for example derived classes.
   *
   * @param scope the scope in which classes are searched.
   * @return the filter to use, or null if no additional filtering is necessary
   */
  public @Nullable Predicate<PsiClass> getClassesFilter(@NotNull GlobalSearchScope scope) {
    return null;
  }

  /**
   * Returns a array of files belonging to the specified package which are not located in any of the package directories.
   *
   * @param psiPackage the package to return the list of files for.
   * @param scope      the scope in which files are searched.
   * @return the array of files.
   */
  public PsiFile @NotNull [] getPackageFiles(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return PsiFile.EMPTY_ARRAY;
  }

  /**
   * Returns the filter to use for filtering the list of files in the directories belonging to a package to exclude files
   * that actually belong to a different package. (For example, in Kotlin the package of a file is determined by its
   * package statement and not by its location in the directory structure, so the files which have a differring package
   * statement need to be excluded.)
   *
   * @param psiPackage the package for which the list of files is requested.
   * @param scope      the scope in which children are requested.
   * @return the filter to use, or null if no additional filtering is necessary.
   */
  public @Nullable Condition<PsiFile> getPackageFilesFilter(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return null;
  }

  /**
   * A method to optimize resolve (to only search classes in a package which might be there)
   */
  public @NotNull Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return getClassNames(getClasses(psiPackage, scope));
  }

  protected static @NotNull Set<String> getClassNames(PsiClass @NotNull [] classes) {
    if (classes.length == 0) {
      return Collections.emptySet();
    }

    Set<String> names = new HashSet<>(classes.length);
    for (PsiClass aClass : classes) {
      ContainerUtil.addIfNotNull(names, aClass.getName());
    }
    return names;
  }

  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull Processor<? super PsiDirectory> consumer) {
    return processPackageDirectories(psiPackage, scope, consumer, false);
  }

  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull Processor<? super PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    return true;
  }

  /**
   * @param psiPackage package to search
   * @param scope scope to search in
   * @param consumer processor that processes every single-file source root that belongs to this package
   * @return true if the consumer never returned false
   */
  public boolean processPackageFiles(@NotNull PsiPackage psiPackage,
                                     @NotNull GlobalSearchScope scope,
                                     @NotNull Processor<? super PsiFile> consumer) {
    return true;
  }

  /**
   * Returns the array of classes in the specified package and in the specified search scope.
   *
   * @param className short name of the class
   * @param psiPackage the package to return the list of classes in.
   * @param scope the scope in which classes are searched.
   * @return the array of classes.
   * @see PsiPackage#getClasses(GlobalSearchScope)
   */
  public PsiClass @NotNull [] getClasses(@Nullable String className, @NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    PsiClass[] allClasses = getClasses(psiPackage, scope);
    if (className == null) return allClasses;
    return filterByName(className, allClasses);
  }

  public static PsiClass @NotNull [] filterByName(@NotNull String className, PsiClass @NotNull [] classes) {
    if (classes.length == 0) return PsiClass.EMPTY_ARRAY;
    if (classes.length == 1) {
      return className.equals(classes[0].getName()) ? classes : PsiClass.EMPTY_ARRAY;
    }
    List<PsiClass> foundClasses = new SmartList<>();
    for (PsiClass psiClass : classes) {
      if (className.equals(psiClass.getName())) {
        foundClasses.add(psiClass);
      }
    }
    return foundClasses.isEmpty() ? PsiClass.EMPTY_ARRAY : foundClasses.toArray(PsiClass.EMPTY_ARRAY);
  }

}
