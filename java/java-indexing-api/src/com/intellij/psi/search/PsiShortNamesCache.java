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
package com.intellij.psi.search;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to retrieve files and Java classes, methods and fields in a project by
 * non-qualified names.
 *
 */
public abstract class PsiShortNamesCache {
  /**
   * Return the composite short names cache, uniting all short name cache instances registered via extensions.
   *
   * @param project the project to return the cache for.
   * @return the cache instance.
   */

  public static PsiShortNamesCache getInstance(Project project) {
    return ServiceManager.getService(project, PsiShortNamesCache.class);
  }
  
  public static final ExtensionPointName<PsiShortNamesCache> EP_NAME = ExtensionPointName.create("com.intellij.java.shortNamesCache");

  /**
   * Returns the list of files with the specified name.
   *
   * @param name the name of the files to find.
   * @return the list of files in the project which have the specified name.
   */
  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    return PsiFile.EMPTY_ARRAY;
  }

  /**
   * Returns the list of names of all files in the project.
   *
   * @return the list of all file names in the project.
   */
  @NotNull
  public String[] getAllFileNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  /**
   * Returns the list of all classes with the specified name in the specified scope.
   *
   * @param name  the non-qualified name of the classes to find.
   * @param scope the scope in which classes are searched.
   * @return the list of found classes.
   */
  @NotNull
  public abstract PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope);

  /**
   * Returns the list of names of all classes in the project and
   * (optionally) libraries.
   *
   * @return the list of all class names.
   */
  @NotNull
  public abstract String[] getAllClassNames();

  public boolean processAllClassNames(Processor<String> processor) {
    return ContainerUtil.process(getAllClassNames(), processor);
  }

  public boolean processAllClassNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    return ContainerUtil.process(getAllClassNames(), processor);
  }

  /**
   * Adds the names of all classes in the project and (optionally) libraries
   * to the specified set.
   *
   * @param dest the set to add the names to.
   */
  public abstract void getAllClassNames(@NotNull HashSet<String> dest);

  /**
   * Returns the list of all methods with the specified name in the specified scope.
   *
   * @param name  the name of the methods to find.
   * @param scope the scope in which methods are searched.
   * @return the list of found methods.
   */
  @NotNull
  public abstract PsiMethod[] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope);

  @NotNull
  public abstract PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount);
  @NotNull
  public abstract PsiField[] getFieldsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount);

  public abstract boolean processMethodsWithName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiMethod> processor);

  public boolean processMethodsWithName(@NonNls @NotNull String name, @NotNull final Processor<? super PsiMethod> processor,
                                                 @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    return processMethodsWithName(name, scope, new Processor<PsiMethod>() {
      @Override
      public boolean process(PsiMethod method) {
        return processor.process(method);
      }
    });
  }

  public boolean processAllMethodNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    return ContainerUtil.process(getAllMethodNames(), processor);
  }

  public boolean processAllFieldNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    return ContainerUtil.process(getAllFieldNames(), processor);
  }

  /**
   * Returns the list of names of all methods in the project and
   * (optionally) libraries.
   *
   * @return the list of all method names.
   */
  @NotNull
  public abstract String[] getAllMethodNames();

  /**
   * Adds the names of all methods in the project and (optionally) libraries
   * to the specified set.
   *
   * @param set the set to add the names to.
   */
  public abstract void getAllMethodNames(@NotNull HashSet<String> set);

  /**
   * Returns the list of all fields with the specified name in the specified scope.
   *
   * @param name  the name of the fields to find.
   * @param scope the scope in which fields are searched.
   * @return the list of found fields.
   */
  @NotNull
  public abstract PsiField[] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope);

  /**
   * Returns the list of names of all fields in the project and
   * (optionally) libraries.
   *
   * @return the list of all field names.
   */
  @NotNull
  public abstract String[] getAllFieldNames();

  /**
   * Adds the names of all methods in the project and (optionally) libraries
   * to the specified set.
   *
   * @param set the set to add the names to.
   */
  public abstract void getAllFieldNames(@NotNull HashSet<String> set);

  public boolean processFieldsWithName(@NotNull String name, @NotNull Processor<? super PsiField> processor,
                                                @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    return ContainerUtil.process(getFieldsByName(name, scope), processor);
  }

  public boolean processClassesWithName(@NotNull String name, @NotNull Processor<? super PsiClass> processor,
                                                 @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    return ContainerUtil.process(getClassesByName(name, scope), processor);
  }
}
