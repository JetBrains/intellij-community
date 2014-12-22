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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Allows to extend the mechanism of locating classes and packages by full-qualified name.
 * Implementations of this interface need to be registered as extensions in order
 * to be picked up by {@link JavaPsiFacade}.
 */
public abstract class PsiElementFinder {
  public static final ExtensionPointName<PsiElementFinder> EP_NAME = ExtensionPointName.create("com.intellij.java.elementFinder");

  /**
   * Searches the specified scope within the project for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope the scope to search.
   * @return the PSI class, or null if no class with such name is found.
   * @see JavaPsiFacade#findClass(String, GlobalSearchScope)
   */
  @Nullable
  public abstract PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Searches the specified scope within the project for classes with the specified full-qualified
   * name and returns all found classes.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope the scope to search.
   * @return the array of found classes, or an empty array if no classes are found.
   * @see JavaPsiFacade#findClasses(String, GlobalSearchScope)
   */
  @NotNull
  public abstract PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Searches the project for the package with the specified full-qualified name and returns one
   * if it is found.
   *
   * @param qualifiedName the full-qualified name of the package to find.
   * @return the PSI package, or null if no package with such name is found.
   * @see JavaPsiFacade#findPackage(String)
   */
  @Nullable
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    return null;
  }

  /**
   * Returns the list of subpackages of the specified package in the specified search scope.
   *
   * @param psiPackage the package to return the list of subpackages for.
   * @param scope the scope in which subpackages are searched.
   * @return the list of subpackages.
   * @see PsiPackage#getSubPackages(GlobalSearchScope)
   */
  @NotNull
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return PsiPackage.EMPTY_ARRAY;
  }

  /**
   * Returns the list of classes in the specified package and in the specified search scope.
   *
   * @param psiPackage the package to return the list of classes in.
   * @param scope the scope in which classes are searched.
   * @return the list of classes.
   * @see PsiPackage#getClasses(GlobalSearchScope)
   */
  @NotNull
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  /**
   * Returns a list of children (classes, subpackages and possibly other elements) belonging to the specified package.
   *
   * @param psiPackage the package to return the list of children for.
   * @param scope      the scope in which children are searched.
   * @return the list of children.
   * @since 14.1
   */
  @NotNull
  public PsiNamedElement[] getChildren(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    Set<PsiNamedElement> children = new HashSet<PsiNamedElement>();
    Collections.addAll(children, getSubPackages(psiPackage, scope));
    Collections.addAll(children, getClasses(psiPackage, scope));
    return children.toArray(new PsiNamedElement[children.size()]);
  }

  /**
   * Returns the filter to use for filtering the list of children for a given package produced by other PsiElementFinder
   * implementations. (For example, the list of children for a Kotlin package includes files directly, rather than classes,
   * so the classes located by the standard Java package children finder need to be excluded.)
   *
   * @param psiPackage the package to return the list of children for.
   * @param scope      the scope in which children are searched.
   * @return the filter to use, or null if no additional filtering is necessary.
   * @since 14.1
   */
  @Nullable
  public Predicate<PsiNamedElement> getPackageChildrenFilter(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return null;
  }

  /**
   * A method to optimize resolve (to only search classes in a package which might be there)
   */
  @NotNull
  public Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return getClassNames(getClasses(psiPackage, scope));
  }

  @NotNull
  protected static Set<String> getClassNames(@NotNull PsiClass[] classes) {
    if (classes.length == 0) {
      return Collections.emptySet();
    }

    final HashSet<String> names = new HashSet<String>();
    for (PsiClass aClass : classes) {
      ContainerUtil.addIfNotNull(aClass.getName(), names);
    }
    return names;
  }

  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull Processor<PsiDirectory> consumer) {
    return processPackageDirectories(psiPackage, scope, consumer, false);
  }

  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull Processor<PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    return true;
  }

  /**
   * Returns the list of classes in the specified package and in the specified search scope.
   *
   * @param className short name of the class
   * @param psiPackage the package to return the list of classes in.
   * @param scope the scope in which classes are searched.
   * @return the list of classes.
   * @see PsiPackage#getClasses(GlobalSearchScope)
   */
  @NotNull
  public PsiClass[] getClasses(@Nullable String className, @NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    PsiClass[] allClasses = getClasses(psiPackage, scope);
    if (className == null) return allClasses;
    return filterByName(className, allClasses);
  }

  @NotNull
  public static PsiClass[] filterByName(@NotNull String className, @NotNull PsiClass[] classes) {
    if (classes.length == 0) return PsiClass.EMPTY_ARRAY;
    if (classes.length == 1) {
      return className.equals(classes[0].getName()) ? classes : PsiClass.EMPTY_ARRAY;
    }
    List<PsiClass> foundClasses = new SmartList<PsiClass>();
    for (PsiClass psiClass : classes) {
      if (className.equals(psiClass.getName())) {
        foundClasses.add(psiClass);
      }
    }
    return foundClasses.isEmpty() ? PsiClass.EMPTY_ARRAY : foundClasses.toArray(new PsiClass[foundClasses.size()]);
  }

}
