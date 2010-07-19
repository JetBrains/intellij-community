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
package com.intellij.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class CompositeShortNamesCache extends PsiShortNamesCache {
  private final List<PsiShortNamesCache> myCaches = new ArrayList<PsiShortNamesCache>();
  private PsiShortNamesCache[] myCacheArray = new PsiShortNamesCache[0];

  public void addCache(PsiShortNamesCache cache) {
    myCaches.add(cache);
    myCacheArray = myCaches.toArray(new PsiShortNamesCache[myCaches.size()]);
  }

  public void runStartupActivity() {
    for (PsiShortNamesCache cache : myCaches) {
      cache.runStartupActivity();
    }
  }

  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    Merger<PsiFile> merger = null;
    for (PsiShortNamesCache cache : myCacheArray) {
      PsiFile[] classes = cache.getFilesByName(name);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<PsiFile>();
        merger.add(classes);
      }
    }
    PsiFile[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiFile.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllFileNames() {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCacheArray) {
      merger.add(cache.getAllFileNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiClass> merger = null;
    for (PsiShortNamesCache cache : myCacheArray) {
      PsiClass[] classes = cache.getClassesByName(name, scope);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<PsiClass>();
        merger.add(classes);
      }
    }
    PsiClass[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllClassNames() {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCacheArray) {
      merger.add(cache.getAllClassNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllClassNames(@NotNull HashSet<String> dest) {
    for (PsiShortNamesCache cache : myCacheArray) {
      cache.getAllClassNames(dest);
    }
  }

  @NotNull
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiMethod> merger = null;
    for (PsiShortNamesCache cache : myCacheArray) {
      PsiMethod[] classes = cache.getMethodsByName(name, scope);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<PsiMethod>();
        merger.add(classes);
      }
    }
    PsiMethod[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull final String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    Merger<PsiMethod> merger = null;
    for (PsiShortNamesCache cache : myCacheArray) {
      PsiMethod[] classes = cache.getMethodsByNameIfNotMoreThan(name, scope, maxCount);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<PsiMethod>();
        merger.add(classes);
      }
    }
    PsiMethod[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllMethodNames() {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCacheArray) {
      merger.add(cache.getAllMethodNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllMethodNames(@NotNull HashSet<String> set) {
    for (PsiShortNamesCache cache : myCacheArray) {
      cache.getAllMethodNames(set);
    }
  }

  @NotNull
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiField> merger = null;
    for (PsiShortNamesCache cache : myCacheArray) {
      PsiField[] classes = cache.getFieldsByName(name, scope);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<PsiField>();
        merger.add(classes);
      }
    }
    PsiField[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllFieldNames() {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCacheArray) {
      merger.add(cache.getAllFieldNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllFieldNames(@NotNull HashSet<String> set) {
    for (PsiShortNamesCache cache : myCacheArray) {
      cache.getAllFieldNames(set);
    }
  }

  private static class Merger<T> {
    private T[] mySingleItem = null;
    private Set<T> myAllItems = null;

    public void add(T[] items) {
      if (items == null || items.length == 0) return;
      if (mySingleItem == null) {
        mySingleItem = items;
        return;
      }
      if (myAllItems == null) {
        myAllItems = new THashSet<T>(Arrays.asList(mySingleItem));
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
    return "Composite cache: " + myCaches;
  }
}
