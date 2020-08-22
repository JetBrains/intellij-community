// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public final class JavaAnonymousClassesHelper {
  private static final Key<ParameterizedCachedValue<Map<PsiAnonymousClass, String>, PsiClass>> ANONYMOUS_CLASS_NAME = Key.create("ANONYMOUS_CLASS_NAME");
  public static final AnonClassProvider ANON_CLASS_PROVIDER = new AnonClassProvider();

  @Nullable
  public static String getName(@NotNull PsiAnonymousClass cls) {
    final PsiClass upper = PsiTreeUtil.getParentOfType(cls, PsiClass.class);
    if (upper == null) {
      return null;
    }
    ParameterizedCachedValue<Map<PsiAnonymousClass, String>, PsiClass> value = upper.getUserData(ANONYMOUS_CLASS_NAME);
    if (value == null) {
      value = CachedValuesManager.getManager(upper.getProject()).createParameterizedCachedValue(ANON_CLASS_PROVIDER, false);
      upper.putUserData(ANONYMOUS_CLASS_NAME, value);
    }
    return value.getValue(upper).get(cls);
  }

  private static class AnonClassProvider implements ParameterizedCachedValueProvider<Map<PsiAnonymousClass, String>, PsiClass> {
    @Override
    public CachedValueProvider.Result<Map<PsiAnonymousClass, String>> compute(final PsiClass upper) {
      final Map<PsiAnonymousClass, String> map = new THashMap<>();
      upper.accept(new JavaRecursiveElementWalkingVisitor() {
        int index;

        @Override
        public void visitAnonymousClass(PsiAnonymousClass aClass) {
          if (upper == aClass) {
            super.visitAnonymousClass(aClass);
            return;
          }
          final PsiExpressionList arguments = aClass.getArgumentList();
          if (arguments != null) {
            for (PsiExpression expression : arguments.getExpressions()) {
              expression.acceptChildren(new JavaRecursiveElementVisitor() {
                @Override
                public void visitAnonymousClass(PsiAnonymousClass aClass) {
                  index++;
                  map.put(aClass, "$" + index);
                }
              });
            }
          }

          index++;
          map.put(aClass, "$" + index);
        }

        @Override
        public void visitClass(PsiClass aClass) {
          if (aClass == upper) {
            super.visitClass(aClass);
          }
        }
      });
      return CachedValueProvider.Result.create(map, upper);
    }
  }
}
