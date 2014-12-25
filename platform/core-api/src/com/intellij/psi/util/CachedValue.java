/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.util;

import org.jetbrains.annotations.NotNull;

/**
 * A wrapper object that holds a computation ({@link #getValueProvider()}) and caches the result of the computation.<p/>
 *
 * The recommended way of creation is to use one of {@link com.intellij.psi.util.CachedValuesManager} methods, e.g.
 * {@link com.intellij.psi.util.CachedValuesManager#getCachedValue(com.intellij.psi.PsiElement, CachedValueProvider)}
 *
 * When {@link #getValue()} is invoked the first time, the computation is run and its result is returned and remembered internally.
 * In subsequent invocations, the result will be reused to avoid running the same code again and again.<p/>
 *
 * The computation will be re-run in the following circumstances:
 * <ol>
 *   <li/>Garbage collector collects the result cached internally (it's kept via a {@link java.lang.ref.SoftReference}).
 *   <li/>IDEA determines that cached value is outdated because some its dependencies are changed. See
 *   {@link com.intellij.psi.util.CachedValueProvider.Result#getDependencyItems()}
 * </ol>
 *
 * The implementation is thread-safe but not atomic, i.e. if several threads request the cached value simultaneously, the computation may
 * be run concurrently on more than one thread.
 *
 * @param <T> The type of the computation result.
 *
 * @see com.intellij.psi.util.CachedValueProvider
 * @see com.intellij.psi.util.CachedValuesManager
 */
public interface CachedValue<T> {

  /**
   * @return cached value if it's already computed and not outdated, newly computed value otherwise
   */
  T getValue();

  /**
   * @return the object calculating the value to cache
   */
  @NotNull
  CachedValueProvider<T> getValueProvider();

  /**
   * @return whether there is a cached result inside this object and it's not outdated
   */
  boolean hasUpToDateValue();
}
