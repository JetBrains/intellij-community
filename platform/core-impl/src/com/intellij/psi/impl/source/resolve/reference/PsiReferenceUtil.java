// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Experimental
public final class PsiReferenceUtil {

  private PsiReferenceUtil() {
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public static <T extends PsiReference> T findReferenceOfClass(PsiReference ref, Class<T> clazz) {
    if (clazz.isInstance(ref)) return (T)ref;
    if (ref instanceof PsiMultiReference) {
      for (PsiReference reference : ((PsiMultiReference)ref).getReferences()) {
        if (clazz.isInstance(reference)) {
          return (T)reference;
        }
      }
    }
    return null;
  }

  public static @NotNull List<PsiReference> unwrapMultiReference(@NotNull PsiReference maybeMultiReference) {
    if (maybeMultiReference instanceof PsiMultiReference) {
      return ContainerUtil.immutableList(((PsiMultiReference)maybeMultiReference).getReferences());
    }
    return ContainerUtil.immutableSingletonList(maybeMultiReference);
  }

}
