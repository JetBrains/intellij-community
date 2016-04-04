/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.psi.util.PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT;

public class ClassInnerStuffCache {
  private final PsiExtensibleClass myClass;
  private final SimpleModificationTracker myTracker;

  public ClassInnerStuffCache(@NotNull PsiExtensibleClass aClass) {
    myClass = aClass;
    myTracker = new SimpleModificationTracker();
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    return CachedValuesManager.getCachedValue(myClass, new CachedValueProvider<PsiMethod[]>() {
      @Nullable
      @Override
      public Result<PsiMethod[]> compute() {
        return Result.create(PsiImplUtil.getConstructors(myClass), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myTracker);
      }
    });
  }

  @NotNull
  public PsiField[] getFields() {
    return CachedValuesManager.getCachedValue(myClass, new CachedValueProvider<PsiField[]>() {
      @Nullable
      @Override
      public Result<PsiField[]> compute() {
        return Result.create(getAllFields(), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myTracker);
      }
    });
  }

  @NotNull
  public PsiMethod[] getMethods() {
    return CachedValuesManager.getCachedValue(myClass, new CachedValueProvider<PsiMethod[]>() {
      @Nullable
      @Override
      public Result<PsiMethod[]> compute() {
        return Result.create(getAllMethods(), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myTracker);
      }
    });
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    return CachedValuesManager.getCachedValue(myClass, new CachedValueProvider<PsiClass[]>() {
      @Nullable
      @Override
      public Result<PsiClass[]> compute() {
        return Result.create(getAllInnerClasses(), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myTracker);
      }
    });
  }

  @Nullable
  public PsiField findFieldByName(String name, boolean checkBases) {
    if (checkBases) {
      return PsiClassImplUtil.findFieldByName(myClass, name, true);
    }
    return CachedValuesManager.getCachedValue(myClass, new CachedValueProvider<Map<String, PsiField>>() {
      @Nullable
      @Override
      public Result<Map<String, PsiField>> compute() {
        return Result.create(getFieldsMap(), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myTracker);
      }
    }).get(name);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    if (checkBases) {
      return PsiClassImplUtil.findMethodsByName(myClass, name, true);
    }
    PsiMethod[] methods = CachedValuesManager.getCachedValue(myClass, new CachedValueProvider<Map<String, PsiMethod[]>>() {
      @Nullable
      @Override
      public Result<Map<String, PsiMethod[]>> compute() {
        return Result.create(getMethodsMap(), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myTracker);
      }
    }).get(name);
    return methods == null ? PsiMethod.EMPTY_ARRAY : methods;
  }

  @Nullable
  public PsiClass findInnerClassByName(final String name, final boolean checkBases) {
    if (checkBases) {
      return PsiClassImplUtil.findInnerByName(myClass, name, true);
    }
    else {
      return CachedValuesManager.getCachedValue(myClass, new CachedValueProvider<Map<String, PsiClass>>() {
        @Nullable
        @Override
        public Result<Map<String, PsiClass>> compute() {
          return Result.create(getInnerClassesMap(), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myTracker);
        }
      }).get(name);
    }
  }

  @Nullable
  public PsiMethod getValuesMethod() {
    return !myClass.isEnum() || myClass.getName() == null ? null : CachedValuesManager.getCachedValue(myClass, new CachedValueProvider<PsiMethod>() {
      @Nullable
      @Override
      public Result<PsiMethod> compute() {
        String text = "public static " + myClass.getName() + "[] values() { }";
        return new Result<PsiMethod>(getSyntheticMethod(text), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myTracker);
      }
    });
  }

  @Nullable
  public PsiMethod getValueOfMethod() {
    return !myClass.isEnum() || myClass.getName() == null ? null : CachedValuesManager.getCachedValue(myClass, new CachedValueProvider<PsiMethod>() {
      @Nullable
      @Override
      public Result<PsiMethod> compute() {
        String text = "public static " + myClass.getName() + " valueOf(java.lang.String name) throws java.lang.IllegalArgumentException { }";
        return new Result<PsiMethod>(getSyntheticMethod(text), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myTracker);
      }
    });
  }

  @NotNull
  private PsiField[] getAllFields() {
    List<PsiField> own = myClass.getOwnFields();
    List<PsiField> ext = PsiAugmentProvider.collectAugments(myClass, PsiField.class);
    return ArrayUtil.mergeCollections(own, ext, PsiField.ARRAY_FACTORY);
  }

  @NotNull
  private PsiMethod[] getAllMethods() {
    List<PsiMethod> own = myClass.getOwnMethods();
    List<PsiMethod> ext = PsiAugmentProvider.collectAugments(myClass, PsiMethod.class);
    return ArrayUtil.mergeCollections(own, ext, PsiMethod.ARRAY_FACTORY);
  }

  @NotNull
  private PsiClass[] getAllInnerClasses() {
    List<PsiClass> own = myClass.getOwnInnerClasses();
    List<PsiClass> ext = PsiAugmentProvider.collectAugments(myClass, PsiClass.class);
    return ArrayUtil.mergeCollections(own, ext, PsiClass.ARRAY_FACTORY);
  }

  @NotNull
  private Map<String, PsiField> getFieldsMap() {
    PsiField[] fields = getFields();
    if (fields.length == 0) return Collections.emptyMap();

    Map<String, PsiField> cachedFields = new THashMap<String, PsiField>();
    for (PsiField field : fields) {
      String name = field.getName();
      if (!(field instanceof ExternallyDefinedPsiElement) || !cachedFields.containsKey(name)) {
        cachedFields.put(name, field);
      }
    }
    return cachedFields;
  }

  @NotNull
  private Map<String, PsiMethod[]> getMethodsMap() {
    PsiMethod[] methods = getMethods();
    if (methods.length == 0) return Collections.emptyMap();

    Map<String, List<PsiMethod>> collectedMethods = ContainerUtil.newHashMap();
    for (PsiMethod method : methods) {
      List<PsiMethod> list = collectedMethods.get(method.getName());
      if (list == null) {
        collectedMethods.put(method.getName(), list = ContainerUtil.newSmartList());
      }
      list.add(method);
    }

    Map<String, PsiMethod[]> cachedMethods = ContainerUtil.newTroveMap();
    for (Map.Entry<String, List<PsiMethod>> entry : collectedMethods.entrySet()) {
      List<PsiMethod> list = entry.getValue();
      cachedMethods.put(entry.getKey(), list.toArray(new PsiMethod[list.size()]));
    }
    return cachedMethods;
  }

  @NotNull
  private Map<String, PsiClass> getInnerClassesMap() {
    PsiClass[] classes = getInnerClasses();
    if (classes.length == 0) return Collections.emptyMap();

    Map<String, PsiClass> cachedInners = new THashMap<String, PsiClass>();
    for (PsiClass psiClass : classes) {
      String name = psiClass.getName();
      if (!(psiClass instanceof ExternallyDefinedPsiElement) || !cachedInners.containsKey(name)) {
        cachedInners.put(name, psiClass);
      }
    }
    return cachedInners;
  }

  private PsiMethod getSyntheticMethod(String text) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
    PsiMethod method = factory.createMethodFromText(text, myClass);
    return new LightMethod(myClass.getManager(), method, myClass) {
      @Override
      public int getTextOffset() {
        return myClass.getTextOffset();
      }
    };
  }

  public void dropCaches() {
    myTracker.incModificationCount();
  }
}