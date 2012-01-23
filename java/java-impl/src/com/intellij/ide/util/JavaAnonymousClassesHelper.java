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
package com.intellij.ide.util;

import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class JavaAnonymousClassesHelper {
  private static final Key<CachedValue<Map<PsiAnonymousClass, String>>> ANONYMOUS_CLASS_NAME = Key.create("ANONYMOUS_CLASS_NAME");

  @Nullable
  public static String getName(@NotNull PsiAnonymousClass cls) {
    final PsiClass upper = PsiTreeUtil.getParentOfType(cls, PsiClass.class);
    if (upper != null) {
      final CachedValue<Map<PsiAnonymousClass, String>> value = upper.getUserData(ANONYMOUS_CLASS_NAME);
      if (value != null && value.hasUpToDateValue()) {
        return value.getValue().get(cls);
      }
      final HashMap<PsiAnonymousClass, String> map = new HashMap<PsiAnonymousClass, String>();
      upper.accept(new JavaRecursiveElementWalkingVisitor() {
        int index = 0;

        @Override
        public void visitAnonymousClass(PsiAnonymousClass aClass) {
          index++;
          map.put(aClass, "$" + String.valueOf(index));
        }

        @Override
        public void visitClass(PsiClass aClass) {
          if (aClass == upper) {
            super.visitClass(aClass);
          }
        }
      });

      final CachedValue<Map<PsiAnonymousClass, String>> cachedValue =
        CachedValuesManager.getManager(cls.getProject()).createCachedValue(new CachedValueProvider<Map<PsiAnonymousClass, String>>() {
          @Override
          public Result<Map<PsiAnonymousClass, String>> compute() {
            return new Result<Map<PsiAnonymousClass, String>>(map, upper);
          }
      });
      upper.putUserData(ANONYMOUS_CLASS_NAME, cachedValue);
      return map.get(cls);
    }
    return null;
  }
}
