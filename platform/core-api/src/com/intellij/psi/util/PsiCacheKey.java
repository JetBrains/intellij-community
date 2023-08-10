// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public final class PsiCacheKey<T, H extends PsiElement> extends Key<SoftReference<Pair<Long, T>>> {
  private final Function<? super H, ? extends T> myFunction;

  private PsiCacheKey(@NonNls @NotNull String name, @NotNull Function<? super H, ? extends T> function) {
    super(name);
    myFunction = function;
  }

  public T getValue(@NotNull H h) {
    T result = getCachedValueOrNull(h);
    if (result != null) {
      return result;
    }

    result = myFunction.fun(h);
    final long count = getModificationCount(h);
    h.putUserData(this, new SoftReference<>(new Pair<>(count, result)));
    return result;
  }

  public @Nullable T getCachedValueOrNull(@NotNull H h) {
    SoftReference<Pair<Long, T>> ref = h.getUserData(this);
    Pair<Long, T> data = dereference(ref);
    if (data == null || data.getFirst() != getModificationCount(h)) {
      return null;
    }

    return data.getSecond();
  }


  /**
   * Return a modification count changed every time anything is changed that {@code place} element might need.
   * For physical PSI, this is equivalent to {@link PsiModificationTracker#getModificationCount()}.
   * For non-physical PSI, modifications of other non-physical PSI that {@code place} can resolve into
   * are included (to the best of platform's knowledge: e.g. the file which contains {@code place}).
   */
  private static long getModificationCount(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    long nonPhysicalStamp = file == null || file.isPhysical() ? 0 : file.getModificationStamp();

    PsiElement root = file != null ? file : element;
    return nonPhysicalStamp + root.getManager().getModificationTracker().getModificationCount();
  }

  /**
   * Creates cache key value
   *
   * @param name        key name
   * @param function    function to reproduce new value when the old value is stale
   * @param <T>         cached value type
   * @param <H>         PSI element type that holds the user data with the cache
   * @return instance
   */
  public static <T, H extends PsiElement> PsiCacheKey<T, H> create(@NonNls @NotNull String name,
                                                                   @NotNull Function<? super H, ? extends T> function) {
    return new PsiCacheKey<>(name, function);
  }

}
