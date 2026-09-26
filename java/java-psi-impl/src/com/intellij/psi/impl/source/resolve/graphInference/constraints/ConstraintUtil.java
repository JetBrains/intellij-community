// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

final class ConstraintUtil {
  static boolean typesEqual(@Nullable PsiType t, @Nullable PsiType t1) {
    if (t == null || t1 == null) return t == null && t1 == null;
    return t.equals(t1) && t.getNullability().equals(t1.getNullability());
  }

  static int typeHashCode(@Nullable PsiType t) {
    return t == null ? 0 : 31 * (31 + t.getNullability().hashCode()) + t.hashCode();
  }
}
