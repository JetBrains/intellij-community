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
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.ExternallyDefinedPsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClassInnerStuffCache {
  private final PsiClass myClass;
  private final MyModificationTracker myTreeChangeTracker;

  private CachedValue<PsiMethod[]> myConstructorsCache;
  private CachedValue<PsiField[]> myFieldsCache;
  private CachedValue<PsiMethod[]> myMethodsCache;
  private CachedValue<Map<String, PsiField>> myFieldsMapCache;
  private CachedValue<Map<String, List<PsiMethod>>> myMethodsMapCache;
  private CachedValue<Map<String, PsiClass>> myInnerClassesMapCache;

  public ClassInnerStuffCache(final PsiClass aClass) {
    myClass = aClass;
    myTreeChangeTracker = new MyModificationTracker();
    buildCaches();
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    final PsiMethod[] constructors = myConstructorsCache.getValue();
    return constructors != null ? constructors : PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiField[] getFields() {
    final PsiField[] fields = myFieldsCache.getValue();
    return fields != null ? fields : PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getMethods() {
    final PsiMethod[] methods = myMethodsCache.getValue();
    return methods != null ? methods : PsiMethod.EMPTY_ARRAY;
  }

  @Nullable
  public PsiField findFieldByName(final String name, final boolean checkBases) {
    if (!checkBases) {
      final Map<String, PsiField> cachedFields = myFieldsMapCache.getValue();
      return cachedFields != null ? cachedFields.get(name) : null;
    }
    return PsiClassImplUtil.findFieldByName(myClass, name, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(final String name, final boolean checkBases) {
    if (!checkBases) {
      final Map<String, List<PsiMethod>> cachedMethods = myMethodsMapCache.getValue();
      if (cachedMethods != null) {
        final List<PsiMethod> methods = cachedMethods.get(name);
        if (methods != null && methods.size() > 0) {
          return methods.toArray(new PsiMethod[methods.size()]);
        }
      }
      return PsiMethod.EMPTY_ARRAY;
    }
    return PsiClassImplUtil.findMethodsByName(myClass, name, checkBases);
  }

  @Nullable
  public PsiClass findInnerClassByName(final String name, final boolean checkBases) {
    if (!checkBases) {
      final Map<String, PsiClass> inners = myInnerClassesMapCache.getValue();
      return inners != null ? inners.get(name) : null;
    }
    return PsiClassImplUtil.findInnerByName(myClass, name, checkBases);
  }

  private void buildCaches() {
    final CachedValuesManager manager = CachedValuesManager.getManager(myClass.getProject());
    final Object[] dependencies = {PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myTreeChangeTracker};

    myConstructorsCache = manager.createCachedValue(new CachedValueProvider<PsiMethod[]>() {
      public Result<PsiMethod[]> compute() {
        return Result.create(PsiImplUtil.getConstructors(myClass), dependencies);
      }
    }, false);

    myFieldsCache = manager.createCachedValue(new CachedValueProvider<PsiField[]>() {
      public Result<PsiField[]> compute() {
        return Result.create(getAllFields(), dependencies);
      }
    }, false);

    myMethodsCache = manager.createCachedValue(new CachedValueProvider<PsiMethod[]>() {
      public Result<PsiMethod[]> compute() {
        return Result.create(getAllMethods(), dependencies);
      }
    }, false);

    myFieldsMapCache = manager.createCachedValue(new CachedValueProvider<Map<String, PsiField>>() {
      public Result<Map<String, PsiField>> compute() {
        return Result.create(getFieldsMap(), dependencies);
      }
    }, false);

    myMethodsMapCache = manager.createCachedValue(new CachedValueProvider<Map<String, List<PsiMethod>>>() {
      public Result<Map<String, List<PsiMethod>>> compute() {
        return Result.create(getMethodsMap(), dependencies);
      }
    }, false);

    myInnerClassesMapCache = manager.createCachedValue(new CachedValueProvider<Map<String, PsiClass>>() {
      public Result<Map<String, PsiClass>> compute() {
        return Result.create(getInnerClassesMap(), dependencies);
      }
    }, false);
  }

  private PsiField[] getAllFields() {
    if (!(myClass instanceof PsiClassImpl)) return myClass.getFields();

    final PsiField[] own = ((PsiClassImpl)myClass).getStubOrPsiChildren(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY);
    final List<PsiField> ext = PsiAugmentProvider.collectAugments(myClass, PsiField.class);
    return ArrayUtil.mergeArrayAndCollection(own, ext, PsiField.ARRAY_FACTORY);
  }

  private PsiMethod[] getAllMethods() {
    if (!(myClass instanceof PsiClassImpl)) return myClass.getMethods();

    final PsiMethod[] own = ((PsiClassImpl)myClass).getStubOrPsiChildren(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY);
    final List<PsiMethod> ext = PsiAugmentProvider.collectAugments(myClass, PsiMethod.class);
    return ArrayUtil.mergeArrayAndCollection(own, ext, PsiMethod.ARRAY_FACTORY);
  }

  @Nullable
  private Map<String, PsiField> getFieldsMap() {
    final PsiField[] fields = getFields();
    if (fields.length == 0) return null;

    final Map<String, PsiField> cachedFields = new THashMap<String, PsiField>();
    for (final PsiField field : fields) {
      final String name = field.getName();
      if (!(field instanceof ExternallyDefinedPsiElement) || !cachedFields.containsKey(name)) {
        cachedFields.put(name, field);
      }
    }
    return cachedFields;
  }

  @Nullable
  private Map<String, List<PsiMethod>> getMethodsMap() {
    final PsiMethod[] methods = getMethods();
    if (methods.length == 0) return null;

    final Map<String, List<PsiMethod>> cachedMethods = new THashMap<String, List<PsiMethod>>();
    for (final PsiMethod method : methods) {
      List<PsiMethod> list = cachedMethods.get(method.getName());
      if (list == null) {
        cachedMethods.put(method.getName(), (list = new ArrayList<PsiMethod>(1)));
      }
      list.add(method);
    }
    return cachedMethods;
  }

  @Nullable
  private Map<String, PsiClass> getInnerClassesMap() {
    final PsiClass[] classes = myClass.getInnerClasses();
    if (classes.length == 0) return null;

    final Map<String, PsiClass> cachedInners = new THashMap<String, PsiClass>();
    for (final PsiClass psiClass : classes) {
      cachedInners.put(psiClass.getName(), psiClass);
    }
    return cachedInners;
  }

  public void dropCaches() {
    myTreeChangeTracker.myCount++;
  }

  private static class MyModificationTracker implements ModificationTracker {
    private long myCount = 0;
    public long getModificationCount() {
      return myCount;
    }
  }
}