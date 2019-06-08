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

import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.RecursionGuard;
import org.jetbrains.annotations.NotNull;

/**
 * A wrapper object that holds a computation ({@link #getValueProvider()}) and caches the result of the computation.
 * The recommended way of creation is to use one of {@link CachedValuesManager#getCachedValue} methods.<p></p>
 *
 * When {@link #getValue()} is invoked the first time, the computation is run and its result is returned and remembered internally.
 * In subsequent invocations, the result will be reused to avoid running the same code again and again.<p/>
 *
 * The computation will be re-run in the following circumstances:
 * <ol>
 *   <li/>Garbage collector collects the result cached internally (it's kept via a {@link java.lang.ref.SoftReference}).
 *   <li/>IDEA determines that cached value is outdated because some its dependencies are changed. See
 *   {@link CachedValueProvider.Result#getDependencyItems()}
 * </ol>
 *
 * The implementation is thread-safe but not atomic, i.e. if several threads request the cached value simultaneously, the computation may
 * be run concurrently on more than one thread. Due to this and unpredictable garbage collection,
 * cached value providers shouldn't have side effects.<p></p>
 *
 * <b>Result equivalence</b>: CachedValue might return a different result even if the previous one
 * is still reachable and not garbage-collected, and dependencies haven't changed. Therefore CachedValue results
 * should be equivalent and interchangeable if they're called multiple times. Examples:
 * <ul>
 *   <li>If PSI declarations are cached, {@link #equals} or at least {@link com.intellij.psi.PsiManager#areElementsEquivalent}
 *   should hold for results from the same CachedValue.</li>
 *   <li>{@link com.intellij.psi.ResolveResult} objects should have equivalent {@code getElement()} values.</li>
 *   <li>Cached arrays or lists should have the same number of elements, and they also should be equivalent and come in the same order.</li>
 *   <li>If the result object's class has a meaningful {@link #equals} method, it should hold.</li>
 * </ul>
 * This is enforced at runtime by occasional checks in {@link com.intellij.util.IdempotenceChecker#checkEquivalence(Object, Object, Class)}.
 * See that method's documentation for further information and advice, when a failure happens.<p></p>
 *
 * <b>Context-independence</b>: if you store the CachedValue in a field or user data of some object {@code X}, then its {@link CachedValueProvider}
 * may only depend on X and parts of global system state that don't change while {@code X} is alive and valid (e.g. application/project components/services).
 * Otherwise re-invoking the CachedValueProvider after invalidation would use outdated data and produce incorrect results,
 * possibly causing exceptions in places far, far away. In particular, the provider may not capture:
 * <ul>
 *   <li>Parameters of a method where CachedValue is created, except for {@code X} itself. Example:
 *   <pre>
 *   PsiElement resolve(PsiElement e, boolean incompleteCode) {
 *     return CachedValuesManager.getCachedValue(e, () -> doResolve(e, incompleteCode)); // WRONG!!!
 *   }
 *   </pre>
 *
 *   </li>
 *   <li>"this" object creating the CachedValue, if {@code X} can outlive it,
 *   or if there can be several non-equivalent instances of "this"-object's class all creating a cached value for the same place</li>
 *   <li>Thread-locals at the moment of creation. If you use them (either directly or via {@link RecursionGuard#currentStack()}),
 *   please try not to. If you really have to, also use {@link RecursionGuard#prohibitResultCaching(Object)}
 *   to ensure values depending on unstable data won't be cached.</li>
 *   <li>PSI elements around {@code X}, when {@code X} is a {@link com.intellij.psi.PsiElement} itself,
 *   as they can change during the lifetime of that PSI element. Example:
 *   <pre>
 *   PsiMethod[] methods = psiClass.getMethods();
 *   return CachedValuesManager.getCachedValue(psiClass, () -> calculateSomeResult(methods)); // WRONG!!!
 *   </pre>
 *   </ul>
 * </ul>
 * This is enforced at runtime by occasional checks in {@link com.intellij.util.CachedValueStabilityChecker}.
 * See that class's documentation for further information and advice, when a failure happens.<p></p>
 *
 * <b>Recursion prevention</b>: The same cached value provider can be re-entered recursively on the same thread,
 * if the computation is inherently cyclic. Note that this is likely to result in {@link StackOverflowError},
 * so avoid such situations at all cost. If there's no other way, use
 * {@link com.intellij.openapi.util.RecursionManager#doPreventingRecursion} instead of custom thread-locals to help get out of the endless loop. Please ensure this call happens inside
 * the {@link CachedValueProvider}, not outside {@link CachedValue#getValue()} call. Otherwise you might get no caching at all, because
 * CachedValue uses {@link RecursionGuard.StackStamp#mayCacheNow()} to prevent caching incomplete values, and even the top-level
 * call would be considered incomplete if it happens inside {@code doPreventingRecursion}.
 *
 * @param <T> The type of the computation result.
 *
 * @see CachedValueProvider
 * @see CachedValuesManager
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

  /**
   * @return if {@link #hasUpToDateValue()}, then a wrapper around the cached value, otherwise null.
   */
  Getter<T> getUpToDateOrNull();
}
