// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IdFilter;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CompositeShortNamesCache extends PsiShortNamesCache {
  private final Project myProject;
  private final Set<Language> myExcludeLanguages;

  public CompositeShortNamesCache(Project project) {
    this(project, /*withoutLanguages*/Collections.emptySet());
  }

  private CompositeShortNamesCache(Project project, Set<Language> excludeLanguages) {
    myProject = project;
    myExcludeLanguages = excludeLanguages;
  }

  @Override
  public @NotNull PsiShortNamesCache withoutLanguages(Set<Language> excludeLanguages) {
    if (excludeLanguages.isEmpty()) return this;

    Set<Language> newExcludeLanguages = CollectionsKt.union(myExcludeLanguages, excludeLanguages);
    return new CompositeShortNamesCache(myProject, newExcludeLanguages);
  }

  @NotNull
  private List<PsiShortNamesCache> getCaches() {
    if (myProject.isDefault()) return Collections.emptyList();

    List<@NotNull PsiShortNamesCache> extensionList = EP_NAME.getExtensionList(myProject);
    if (!myExcludeLanguages.isEmpty()) {
      return extensionList
        .stream()
        .filter(cache -> !myExcludeLanguages.contains(cache.getLanguage()))
        .toList();
    } else {
      return extensionList;
    }
  }

  @Override
  public @NotNull PsiFile @NotNull [] getFilesByName(@NotNull String name) {
    Merger<PsiFile> merger = null;
    for (PsiShortNamesCache cache : getCaches()) {
      PsiFile[] classes = cache.getFilesByName(name);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(classes);
      }
    }
    return getMergerResult(merger, PsiFile.EMPTY_ARRAY);
  }

  @Override
  public @NotNull String @NotNull [] getAllFileNames() {
    Merger<String> merger = new Merger<>();
    for (PsiShortNamesCache cache : getCaches()) {
      merger.add(cache.getAllFileNames());
    }
    return getMergerResult(merger, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  private static <T> @NotNull T @NotNull [] getMergerResult(@Nullable Merger<T> merger, T[] emptyArray) {
    if (merger == null || merger.myAllItems == null && merger.mySingleItem == null) return emptyArray;
    return merger.myAllItems == null ? merger.mySingleItem : merger.myAllItems.toArray(emptyArray);
  }

  @Override
  public @NotNull PsiClass @NotNull [] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiClass> merger = null;
    for (PsiShortNamesCache cache : getCaches()) {
      PsiClass[] classes = cache.getClassesByName(name, scope);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(classes);
      }
    }
    return getMergerResult(merger, PsiClass.EMPTY_ARRAY);
  }

  @Override
  public @NotNull String @NotNull [] getAllClassNames() {
    Merger<String> merger = new Merger<>();
    for (PsiShortNamesCache cache : getCaches()) {
      String[] names = cache.getAllClassNames();
      merger.add(names);
    }
    return getMergerResult(merger, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  @Override
  public boolean processAllClassNames(@NotNull Processor<? super String> processor) {
    CommonProcessors.UniqueProcessor<String> uniqueProcessor = new CommonProcessors.UniqueProcessor<>(processor);
    for (PsiShortNamesCache cache : getCaches()) {
      if (!cache.processAllClassNames(uniqueProcessor)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean processAllClassNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, IdFilter filter) {
    for (PsiShortNamesCache cache : getCaches()) {
      if (!cache.processAllClassNames(processor, scope, filter)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean processAllMethodNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, IdFilter filter) {
    for (PsiShortNamesCache cache : getCaches()) {
      if (!cache.processAllMethodNames(processor, scope, filter)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean processAllFieldNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, IdFilter filter) {
    for (PsiShortNamesCache cache : getCaches()) {
      if (!cache.processAllFieldNames(processor, scope, filter)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public @NotNull PsiMethod @NotNull [] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiMethod> merger = null;
    for (PsiShortNamesCache cache : getCaches()) {
      PsiMethod[] methods = cache.getMethodsByName(name, scope);
      if (methods.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(methods);
      }
    }
    return getMergerResult(merger, PsiMethod.EMPTY_ARRAY);
  }

  @Override
  public @NotNull PsiMethod @NotNull [] getMethodsByNameIfNotMoreThan(@NonNls @NotNull final String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    Merger<PsiMethod> merger = null;
    for (PsiShortNamesCache cache : getCaches()) {
      PsiMethod[] methods = cache.getMethodsByNameIfNotMoreThan(name, scope, maxCount);
      if (methods.length == maxCount) return methods;
      if (methods.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(methods);
      }
    }
    return getMergerResult(merger, PsiMethod.EMPTY_ARRAY);
  }

  @Override
  public @NotNull PsiField @NotNull [] getFieldsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    Merger<PsiField> merger = null;
    for (PsiShortNamesCache cache : getCaches()) {
      PsiField[] fields = cache.getFieldsByNameIfNotMoreThan(name, scope, maxCount);
      if (fields.length == maxCount) return fields;
      if (fields.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(fields);
      }
    }
    return getMergerResult(merger, PsiField.EMPTY_ARRAY);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull GlobalSearchScope scope,
                                        @NotNull Processor<? super PsiMethod> processor) {
    return processMethodsWithName(name, processor, scope, null);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull Processor<? super PsiMethod> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter idFilter) {
    for (PsiShortNamesCache cache : getCaches()) {
      if (!cache.processMethodsWithName(name, processor, scope, idFilter)) return false;
    }
    return true;
  }

  @Override
  public @NotNull String @NotNull [] getAllMethodNames() {
    Merger<String> merger = new Merger<>();
    for (PsiShortNamesCache cache : getCaches()) {
      merger.add(cache.getAllMethodNames());
    }
    return getMergerResult(merger, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  @Override
  public @NotNull PsiField @NotNull [] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiField> merger = null;
    for (PsiShortNamesCache cache : getCaches()) {
      PsiField[] classes = cache.getFieldsByName(name, scope);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(classes);
      }
    }
    return getMergerResult(merger, PsiField.EMPTY_ARRAY);
  }

  @Override
  public @NotNull String @NotNull [] getAllFieldNames() {
    Merger<String> merger = null;
    for (PsiShortNamesCache cache : getCaches()) {
      String[] classes = cache.getAllFieldNames();
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(classes);
      }
    }
    return getMergerResult(merger, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  @Override
  public boolean processFieldsWithName(@NotNull String key,
                                       @NotNull Processor<? super PsiField> processor,
                                       @NotNull GlobalSearchScope scope,
                                       @Nullable IdFilter filter) {
    for (PsiShortNamesCache cache : getCaches()) {
      if (!cache.processFieldsWithName(key, processor, scope, filter)) return false;
    }
    return true;
  }

  @Override
  public boolean processClassesWithName(@NotNull String key,
                                        @NotNull Processor<? super PsiClass> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    for (PsiShortNamesCache cache : getCaches()) {
      if (!cache.processClassesWithName(key, processor, scope, filter)) return false;
    }
    return true;
  }

  private static class Merger<T> {
    private T[] mySingleItem;
    private Set<T> myAllItems;

    void add(T @NotNull [] items) {
      if (items.length == 0) return;
      if (mySingleItem == null) {
        mySingleItem = items;
        return;
      }
      if (myAllItems == null) {
        T[] elements = mySingleItem;
        myAllItems = ContainerUtil.addAll(new HashSet<>(elements.length), elements);
      }
      ContainerUtil.addAll(myAllItems, items);
    }
  }

  @Override
  public String toString() {
    return "Composite cache: " + Collections.singletonList(getCaches());
  }
}
