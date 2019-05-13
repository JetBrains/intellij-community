/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.reference.SoftReference;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiCacheKey<T, H extends PsiElement> extends Key<SoftReference<Pair<Long, T>>> {
  private final Function<? super H, ? extends T> myFunction;
  /**
   * One of {@link com.intellij.psi.util.PsiModificationTracker} constants that marks when to flush cache
   */
  @NotNull
  private final Key<?> myModifyCause;

  private PsiCacheKey(@NonNls @NotNull String name, @NotNull Function<? super H, ? extends T> function, @NotNull Key<?> modifyCause) {
    super(name);
    myFunction = function;
    myModifyCause = modifyCause;
  }

  public final T getValue(@NotNull H h) {
    T result = getCachedValueOrNull(h);
    if (result != null) {
      return result;
    }

    result = myFunction.fun(h);
    final long count = getModificationCount(h);
    h.putUserData(this, new SoftReference<>(new Pair<>(count, result)));
    return result;
  }

  @Nullable
  public final T getCachedValueOrNull(@NotNull H h) {
    SoftReference<Pair<Long, T>> ref = h.getUserData(this);
    Pair<Long, T> data = SoftReference.dereference(ref);
    if (data == null || data.getFirst() != getModificationCount(h)) {
      return null;
    }

    return data.getSecond();
  }


  /**
   * Gets modification count from tracker based on {@link #myModifyCause}
   *
   * @param element track to get modification count from
   * @return modification count
   * @throws AssertionError if {@link #myModifyCause} is junk
   */
  private long getModificationCount(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    long fileStamp = file == null || file.isPhysical() ? 0 : file.getModificationStamp();
    PsiModificationTracker tracker = file == null ? element.getManager().getModificationTracker()
                                                  : file.getManager().getModificationTracker();

    if (myModifyCause.equals(PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT)) {
      return fileStamp + tracker.getJavaStructureModificationCount();
    }
    if (myModifyCause.equals(PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT)) {
      return fileStamp + tracker.getOutOfCodeBlockModificationCount();
    }
    if (myModifyCause.equals(PsiModificationTracker.MODIFICATION_COUNT)) {
      return fileStamp + tracker.getModificationCount();
    }
    throw new AssertionError("No modification tracker found for key " + myModifyCause);
  }

  /**
   * Creates cache key value
   *
   * @param name        key name
   * @param function    function to reproduce new value when old value is stale
   * @param modifyCause one one {@link com.intellij.psi.util.PsiModificationTracker}'s constants that marks when to flush cache
   * @param <T>         value type
   * @param <H>         key type
   * @return instance
   */
  public static <T, H extends PsiElement> PsiCacheKey<T, H> create(@NonNls @NotNull String name,
                                                                   @NotNull Function<? super H, ? extends T> function,
                                                                   @NotNull Key<?> modifyCause) {
    return new PsiCacheKey<>(name, function, modifyCause);
  }

  /**
   * Creates cache key value using {@link com.intellij.psi.util.PsiModificationTracker#JAVA_STRUCTURE_MODIFICATION_COUNT} as
   * modification count to flush cache
   *
   * @param name     key name
   * @param function function to reproduce new value when old value is stale
   * @param <T>      value type
   * @param <H>      key type
   * @return instance
   */
  public static <T, H extends PsiElement> PsiCacheKey<T, H> create(@NonNls @NotNull String name, @NotNull Function<? super H, ? extends T> function) {
    return create(name, function, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
  }
}
