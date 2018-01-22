/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.IdFilter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

public class CompositeShortNamesCache extends PsiShortNamesCache {
  private final PsiShortNamesCache[] myCaches;

  public CompositeShortNamesCache(Project project) {
    myCaches = project.isDefault() ? new PsiShortNamesCache[0] : project.getExtensions(PsiShortNamesCache.EP_NAME);
  }

  @Override
  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    Merger<PsiFile> merger = null;
    for (PsiShortNamesCache cache : myCaches) {
      PsiFile[] classes = cache.getFilesByName(name);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(classes);
      }
    }
    PsiFile[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiFile.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String[] getAllFileNames() {
    Merger<String> merger = new Merger<>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getAllFileNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  @NotNull
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiClass> merger = null;
    for (PsiShortNamesCache cache : myCaches) {
      PsiClass[] classes = cache.getClassesByName(name, scope);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(classes);
      }
    }
    PsiClass[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiClass.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String[] getAllClassNames() {
    Merger<String> merger = new Merger<>();
    for (PsiShortNamesCache cache : myCaches) {
      String[] names = cache.getAllClassNames();
      merger.add(names);
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public boolean processAllClassNames(Processor<String> processor) {
    CommonProcessors.UniqueProcessor<String> uniqueProcessor = new CommonProcessors.UniqueProcessor<>(processor);
    for (PsiShortNamesCache cache : myCaches) {
      if (!cache.processAllClassNames(uniqueProcessor)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean processAllClassNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    for (PsiShortNamesCache cache : myCaches) {
      if (!cache.processAllClassNames(processor, scope, filter)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean processAllMethodNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    for (PsiShortNamesCache cache : myCaches) {
      if (!cache.processAllMethodNames(processor, scope, filter)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean processAllFieldNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    for (PsiShortNamesCache cache : myCaches) {
      if (!cache.processAllFieldNames(processor, scope, filter)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void getAllClassNames(@NotNull HashSet<String> dest) {
    for (PsiShortNamesCache cache : myCaches) {
      cache.getAllClassNames(dest);
    }
  }

  @Override
  @NotNull
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiMethod> merger = null;
    for (PsiShortNamesCache cache : myCaches) {
      PsiMethod[] methods = cache.getMethodsByName(name, scope);
      if (methods.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(methods);
      }
    }
    PsiMethod[] result = merger == null ? null : merger.getResult();
    return result == null ? PsiMethod.EMPTY_ARRAY : result;
  }

  @Override
  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull final String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    Merger<PsiMethod> merger = null;
    for (PsiShortNamesCache cache : myCaches) {
      PsiMethod[] methods = cache.getMethodsByNameIfNotMoreThan(name, scope, maxCount);
      if (methods.length == maxCount) return methods;
      if (methods.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(methods);
      }
    }
    PsiMethod[] result = merger == null ? null : merger.getResult();
    return result == null ? PsiMethod.EMPTY_ARRAY : result;
  }

  @NotNull
  @Override
  public PsiField[] getFieldsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    Merger<PsiField> merger = null;
    for (PsiShortNamesCache cache : myCaches) {
      PsiField[] fields = cache.getFieldsByNameIfNotMoreThan(name, scope, maxCount);
      if (fields.length == maxCount) return fields;
      if (fields.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(fields);
      }
    }
    PsiField[] result = merger == null ? null : merger.getResult();
    return result == null ? PsiField.EMPTY_ARRAY : result;
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull GlobalSearchScope scope,
                                        @NotNull Processor<PsiMethod> processor) {
    return processMethodsWithName(name, processor, scope, null);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull Processor<? super PsiMethod> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter idFilter) {
    for (PsiShortNamesCache cache : myCaches) {
      if (!cache.processMethodsWithName(name, processor, scope, idFilter)) return false;
    }
    return true;
  }

  @Override
  @NotNull
  public String[] getAllMethodNames() {
    Merger<String> merger = new Merger<>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getAllMethodNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void getAllMethodNames(@NotNull HashSet<String> set) {
    for (PsiShortNamesCache cache : myCaches) {
      cache.getAllMethodNames(set);
    }
  }

  @Override
  @NotNull
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiField> merger = null;
    for (PsiShortNamesCache cache : myCaches) {
      PsiField[] classes = cache.getFieldsByName(name, scope);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(classes);
      }
    }
    PsiField[] result = merger == null ? null : merger.getResult();
    return result == null ? PsiField.EMPTY_ARRAY : result;
  }

  @Override
  @NotNull
  public String[] getAllFieldNames() {
    Merger<String> merger = null;
    for (PsiShortNamesCache cache : myCaches) {
      String[] classes = cache.getAllFieldNames();
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<>();
        merger.add(classes);
      }
    }
    String[] result = merger == null ? null : merger.getResult();
    return result == null ? ArrayUtil.EMPTY_STRING_ARRAY : result;
  }

  @Override
  public void getAllFieldNames(@NotNull HashSet<String> set) {
    for (PsiShortNamesCache cache : myCaches) {
      cache.getAllFieldNames(set);
    }
  }

  @Override
  public boolean processFieldsWithName(@NotNull String key,
                                       @NotNull Processor<? super PsiField> processor,
                                       @NotNull GlobalSearchScope scope,
                                       @Nullable IdFilter filter) {
    for (PsiShortNamesCache cache : myCaches) {
      if (!cache.processFieldsWithName(key, processor, scope, filter)) return false;
    }
    return true;
  }

  @Override
  public boolean processClassesWithName(@NotNull String key,
                                        @NotNull Processor<? super PsiClass> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    for (PsiShortNamesCache cache : myCaches) {
      if (!cache.processClassesWithName(key, processor, scope, filter)) return false;
    }
    return true;
  }

  private static class Merger<T> {
    private T[] mySingleItem;
    private Set<T> myAllItems;

    public void add(@NotNull T[] items) {
      if (items.length == 0) return;
      if (mySingleItem == null) {
        mySingleItem = items;
        return;
      }
      if (myAllItems == null) {
        T[] elements = mySingleItem;
        myAllItems = ContainerUtil.addAll(new THashSet<>(elements.length), elements);
      }
      ContainerUtil.addAll(myAllItems, items);
    }

    public T[] getResult() {
      if (myAllItems == null) return mySingleItem;
      return myAllItems.toArray(mySingleItem);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Override
  public String toString() {
    return "Composite cache: " + Arrays.asList(myCaches);
  }
}
