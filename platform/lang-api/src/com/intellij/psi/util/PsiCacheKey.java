/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;

public class PsiCacheKey<T,H extends PsiElement> extends Key<Pair<Long, T>> {
  private final Function<H,T> myFunction;

  private PsiCacheKey(@NonNls String name, final Function<H, T> function) {
    super(name);
    myFunction = function;
  }

  public final T getValue(H h) {
    Pair<Long, T> data = h.getUserData(this);
    final long count = h.getManager().getModificationTracker().getJavaStructureModificationCount();
    if (data == null || data.getFirst() != count) {
      h.putUserData(this, data = new Pair<Long, T>(count, myFunction.fun(h)));
    }

    return data.getSecond();
  }

  public static <T,H extends PsiElement> PsiCacheKey<T,H> create(@NonNls String name, final Function<H, T> function) {
    return new PsiCacheKey<T,H>(name, function);
  }
}
