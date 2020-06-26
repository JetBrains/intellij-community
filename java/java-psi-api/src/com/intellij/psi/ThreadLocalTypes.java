// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ThreadLocalTypes {
  private static final RecursionGuard<ThreadLocalTypes> ourGuard = RecursionManager.createGuard("ThreadLocalTypes");
  private final Map<PsiElement, PsiType> myMap = new HashMap<>();
  private final boolean myProhibitCaching;

  private ThreadLocalTypes(boolean prohibitCaching) {
    myProhibitCaching = prohibitCaching;
  }

  @Nullable
  public static PsiType getElementType(@NotNull PsiElement psi) {
    List<? extends ThreadLocalTypes> stack = ourGuard.currentStack();
    for (int i = stack.size() - 1; i >= 0; i--) {
      ThreadLocalTypes types = stack.get(i);
      PsiType type = types.myMap.get(psi);
      if (type != null) {
        if (types.myProhibitCaching) {
          ourGuard.prohibitResultCaching(types);
        }
        return type;
      }
    }
    return null;
  }

  public static boolean hasBindingFor(@NotNull PsiElement psi) {
    List<? extends ThreadLocalTypes> stack = ourGuard.currentStack();
    for (int i = stack.size() - 1; i >= 0; i--) {
      ThreadLocalTypes types = stack.get(i);
      if (types.myMap.containsKey(psi)) {
        if (types.myProhibitCaching) {
          ourGuard.prohibitResultCaching(types);
        }
        return true;
      }
    }
    return false;
  }

  public static <T> T performWithTypes(@NotNull Function<ThreadLocalTypes, T> action) {
    return performWithTypes(action, true);
  }

  public static <T> T performWithTypes(@NotNull Function<ThreadLocalTypes, T> action,
                                       boolean prohibitCaching) {
    ThreadLocalTypes types = new ThreadLocalTypes(prohibitCaching);
    return ourGuard.doPreventingRecursion(types, false, () -> action.apply(types));
  }

  public void forceType(@NotNull PsiElement psi, @Nullable PsiType type) {
    myMap.put(psi, type);
  }

}
