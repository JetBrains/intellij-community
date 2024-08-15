// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Allows to retrieve files and Java classes, methods and fields in a project by non-qualified names.
 */
public abstract class PsiShortNamesCache {
  /**
   * Return the composite short names cache, uniting all short name cache instances registered via extensions.
   *
   * @param project the project to return the cache for.
   * @return the cache instance.
   */
  public static PsiShortNamesCache getInstance(Project project) {
    return project.getService(PsiShortNamesCache.class);
  }

  public static final ExtensionPointName<PsiShortNamesCache> EP_NAME = ExtensionPointName.create("com.intellij.java.shortNamesCache");

  /**
   * Returns the array of files with the specified name.
   *
   * @param name the name of the files to find.
   * @return the array of files in the project which have the specified name.
   */
  public PsiFile @NotNull [] getFilesByName(@NotNull String name) {
    return PsiFile.EMPTY_ARRAY;
  }

  /**
   * Returns the array of names of all files in the project.
   *
   * @return the array of all file names in the project.
   */
  public String @NotNull [] getAllFileNames() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  /**
   * Returns the array of all classes with the specified name in the specified scope.
   *
   * @param name  the non-qualified name of the classes to find.
   * @param scope the scope in which classes are searched.
   * @return the array of found classes.
   */
  public abstract @NotNull PsiClass @NotNull [] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope);

  /**
   * Returns the array of names of all classes in the project and
   * (optionally) libraries.
   *
   * @return the array of all class names.
   */
  public abstract @NotNull String @NotNull [] getAllClassNames();

  public boolean processAllClassNames(@NotNull Processor<? super String> processor) {
    return ContainerUtil.process(getAllClassNames(), processor);
  }

  public boolean processAllClassNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    return ContainerUtil.process(getAllClassNames(), processor);
  }

  /**
   * Returns the array of all methods with the specified name in the specified scope.
   *
   * @param name  the name of the methods to find.
   * @param scope the scope in which methods are searched.
   * @return the array of found methods.
   */
  public abstract @NotNull PsiMethod @NotNull [] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope);

  public abstract @NotNull PsiMethod @NotNull [] getMethodsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount);

  public abstract @NotNull PsiField @NotNull [] getFieldsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount);

  public abstract boolean processMethodsWithName(@NonNls @NotNull String name,
                                                 @NotNull GlobalSearchScope scope,
                                                 @NotNull Processor<? super PsiMethod> processor);

  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull final Processor<? super PsiMethod> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    return processMethodsWithName(name, scope, processor);
  }

  public boolean processAllMethodNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    return ContainerUtil.process(getAllMethodNames(), processor);
  }

  public boolean processAllFieldNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    return ContainerUtil.process(getAllFieldNames(), processor);
  }

  /**
   * Returns the array of names of all methods in the project and
   * (optionally) libraries.
   *
   * @return the array of all method names.
   */
  public abstract @NotNull String @NotNull [] getAllMethodNames();

  /**
   * Returns the array of all fields with the specified name in the specified scope.
   *
   * @param name  the name of the fields to find.
   * @param scope the scope in which fields are searched.
   * @return the array of found fields.
   */
  public abstract @NotNull PsiField @NotNull [] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope);

  /**
   * Returns the array of names of all fields in the project and
   * (optionally) libraries.
   *
   * @return the array of all field names.
   */
  public abstract @NotNull String @NotNull [] getAllFieldNames();

  public boolean processFieldsWithName(@NotNull String name,
                                       @NotNull Processor<? super PsiField> processor,
                                       @NotNull GlobalSearchScope scope,
                                       @Nullable IdFilter filter) {
    return ContainerUtil.process(getFieldsByName(name, scope), processor);
  }

  public boolean processClassesWithName(@NotNull String name,
                                        @NotNull Processor<? super PsiClass> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    return ContainerUtil.process(getClassesByName(name, scope), processor);
  }


  /**
   * Determines for which language the current {@link PsiShortNamesCache} provides the declarations.
   * <p>
   * The default is {@link Language#ANY}
   */
  @ApiStatus.Internal
  public @NotNull Language getLanguage() {
    return Language.ANY;
  }


  /**
   * Returns a new instance of {@link PsiShortNamesCache} which will provide declarations for all the languages except for the ones specified via {@code languages}
   * <p>
   * This method is implemented only in the {@link PsiShortNamesCache}, which is registered as a project service.
   * For other implementations, it throws a {@link UnsupportedOperationException}.
   *
   * @see #getLanguage
   */
  @ApiStatus.Internal
  public @NotNull PsiShortNamesCache withoutLanguages(Set<Language> languages) {
    throw new UnsupportedOperationException();
  }

  @ApiStatus.Internal
  public @NotNull PsiShortNamesCache withoutLanguages(Language... languages) {
    return withoutLanguages(Set.of(languages));
  }
}