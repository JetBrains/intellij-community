// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.containers.ObjectIntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class JavaLocalClassesHelper {
  private static final Key<ParameterizedCachedValue<Map<PsiClass, String>, PsiClass>>
    LOCAL_CLASS_NAME = Key.create("LOCAL_CLASS_NAME");
  private static final LocalClassProvider LOCAL_CLASS_PROVIDER = new LocalClassProvider();

  /**
   * Returns the part of the class name suitable for being appended to the containing class' binary name to produce the local class' binary
   * name, see {@link ClassUtil#getBinaryClassName} and JLS 13.1.
   *
   * <p>For example, if the binary name of a local class is {@code com.example.Foo$1Local}, then this method will return the {@code $1Local}
   * part.
   */
  public static @Nullable String getName(@NotNull PsiClass cls) {
    if (!PsiUtil.isLocalClass(cls)) {
      throw new IllegalArgumentException("class " + cls + " must be a local class");
    }
    final PsiClass upper = PsiTreeUtil.getParentOfType(cls, PsiClass.class);
    if (upper == null) {
      return null;
    }
    ParameterizedCachedValue<Map<PsiClass, String>, PsiClass> value = upper.getUserData(LOCAL_CLASS_NAME);
    if (value == null) {
      value = CachedValuesManager.getManager(upper.getProject()).createParameterizedCachedValue(LOCAL_CLASS_PROVIDER, false);
      upper.putUserData(LOCAL_CLASS_NAME, value);
    }
    return value.getValue(upper).get(cls);
  }

  private static final class LocalClassProvider implements ParameterizedCachedValueProvider<Map<PsiClass, String>, PsiClass> {
    @Override
    public CachedValueProvider.Result<Map<PsiClass, String>> compute(final PsiClass upper) {
      final Map<PsiClass, String> map = new HashMap<>();
      upper.accept(new JavaRecursiveElementWalkingVisitor() {
        final ObjectIntMap<String> indexByName = new ObjectIntHashMap<>();

        @Override
        public void visitClass(@NotNull PsiClass aClass) {
          if (aClass == upper) {
            super.visitClass(aClass);
          } else if (PsiUtil.isLocalClass(aClass)) {
            String name = aClass.getName();
            if (name != null) {
              int index = indexByName.getOrDefault(name, 0) + 1;
              indexByName.put(name, index);
              map.put(aClass, "$" + index + name);
            }
          }
        }
      });
      return CachedValueProvider.Result.create(map, upper);
    }
  }
}
