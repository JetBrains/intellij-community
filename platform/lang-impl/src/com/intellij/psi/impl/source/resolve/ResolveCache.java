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

package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ResolveCache {
  private static final Key<MapPair<PsiPolyVariantReference, Reference<ResolveResult[]>>> JAVA_RESOLVE_MAP = Key.create("ResolveCache.JAVA_RESOLVE_MAP");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP = Key.create("ResolveCache.RESOLVE_MAP");
  private static final Key<MapPair<PsiPolyVariantReference, Reference<ResolveResult[]>>> JAVA_RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.JAVA_RESOLVE_MAP_INCOMPLETE");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.RESOLVE_MAP_INCOMPLETE");

  private final Map<PsiPolyVariantReference,Reference<ResolveResult[]>>[] myPolyVariantResolveMaps = new Map[4];
  private final Map<PsiReference,Reference<PsiElement>>[] myResolveMaps = new Map[4];
  private final AtomicInteger myClearCount = new AtomicInteger(0);
  private final PsiManagerEx myManager;

  private final List<Runnable> myRunnablesToRunOnDropCaches = ContainerUtil.createEmptyCOWList();
  private final RecursionGuard myGuard = RecursionManager.createGuard("resolveCache");

  public interface AbstractResolver<TRef extends PsiReference,TResult> {
    TResult resolve(TRef ref, boolean incompleteCode);
  }
  public interface PolyVariantResolver<T extends PsiPolyVariantReference> extends AbstractResolver<T,ResolveResult[]> {
  }

  public interface Resolver extends AbstractResolver<PsiReference,PsiElement>{
  }

  public ResolveCache(PsiManagerEx manager) {
    myManager = manager;
    myPolyVariantResolveMaps[0] = getOrCreateWeakMap(JAVA_RESOLVE_MAP, true);
    myPolyVariantResolveMaps[1] = getOrCreateWeakMap(JAVA_RESOLVE_MAP_INCOMPLETE, true);
    myResolveMaps[0] = getOrCreateWeakMap(RESOLVE_MAP, true);
    myResolveMaps[1] = getOrCreateWeakMap(RESOLVE_MAP_INCOMPLETE, true);

    myPolyVariantResolveMaps[2] = getOrCreateWeakMap(JAVA_RESOLVE_MAP, false);
    myPolyVariantResolveMaps[3] = getOrCreateWeakMap(JAVA_RESOLVE_MAP_INCOMPLETE, false);

    myResolveMaps[2] = getOrCreateWeakMap(RESOLVE_MAP, false);
    myResolveMaps[3] = getOrCreateWeakMap(RESOLVE_MAP_INCOMPLETE, false);
  }

  public void clearCache() {
    myClearCount.incrementAndGet();
    myPolyVariantResolveMaps[0].clear();
    myPolyVariantResolveMaps[1].clear();
    myResolveMaps[0].clear();
    myResolveMaps[1].clear();

    myPolyVariantResolveMaps[2].clear();
    myPolyVariantResolveMaps[3].clear();

    myResolveMaps[2].clear();
    myResolveMaps[3].clear();

    for (Runnable r : myRunnablesToRunOnDropCaches) {
      r.run();
    }
  }

  public void addRunnableToRunOnDropCaches(Runnable r) {
    myRunnablesToRunOnDropCaches.add(r);
  }

  @Nullable
  private <TRef extends PsiReference, TResult> TResult resolve(final TRef ref,
                                        final AbstractResolver<TRef, TResult> resolver,
                                        final Map<? super TRef,Reference<TResult>>[] maps,
                                        boolean needToPreventRecursion,
                                        final boolean incompleteCode) {
    ProgressManager.checkCanceled();

    final int clearCountOnStart = myClearCount.intValue();

    final boolean physical = ref.getElement().isPhysical();
    TResult result = getCached(ref, maps, physical, incompleteCode);
    if (result != null) {
      return result;
    }

    Computable<TResult> computable = new Computable<TResult>() {
      @Override
      public TResult compute() {
        return resolver.resolve(ref, incompleteCode);
      }
    };

    RecursionGuard.StackStamp stamp = myGuard.markStack();
    result = needToPreventRecursion ? myGuard.doPreventingRecursion(ref, true, computable) : computable.compute();
    if (stamp.mayCacheNow()) {
      cache(ref, result, maps, physical, incompleteCode, clearCountOnStart);
    }
    return result;
  }

   public <T extends PsiPolyVariantReference> ResolveResult[] resolveWithCaching(T ref,
                                            PolyVariantResolver<T> resolver,
                                            boolean needToPreventRecursion,
                                            boolean incompleteCode) {
    ResolveResult[] result = resolve(ref, resolver, myPolyVariantResolveMaps, needToPreventRecursion, incompleteCode);
    return result == null ? ResolveResult.EMPTY_ARRAY : result;
  }

  public PsiElement resolveWithCaching(PsiReference ref,
                                       Resolver resolver,
                                       boolean needToPreventRecursion,
                                       boolean incompleteCode) {
    return resolve(ref, resolver, myResolveMaps, needToPreventRecursion, incompleteCode);
  }

  private static int getIndex(boolean physical, boolean incompleteCode){
    return (physical ? 0 : 1) << 1 | (incompleteCode ? 1 : 0);
  }

  private static <TRef, TResult> TResult getCached(TRef ref, Map<? super TRef,Reference<TResult>>[] maps, boolean physical, boolean incompleteCode){
    int index = getIndex(physical, incompleteCode);
    Reference<TResult> reference = maps[index].get(ref);
    if(reference == null) return null;
    return reference.get();
  }

  private <TRef extends PsiReference, TResult> void cache(TRef ref, TResult result, Map<? super TRef,Reference<TResult>>[] maps, boolean physical, boolean incompleteCode, final int clearCountOnStart) {
    if (clearCountOnStart != myClearCount.intValue() && result != null) return;

    int index = getIndex(physical, incompleteCode);
    maps[index].put(ref, new SoftReference<TResult>(result/*, myQueue*/));
  }

  public <K,V> ConcurrentMap<K,V> getOrCreateWeakMap(final Key<MapPair<K, V>> key, boolean forPhysical) {
    MapPair<K, V> pair = myManager.getUserData(key);
    if (pair == null) {
      pair = myManager.putUserDataIfAbsent(key, new MapPair<K,V>());

      final MapPair<K, V> _pair = pair;
      myManager.registerRunnableToRunOnChange(
        new Runnable() {
          public void run() {
            myClearCount.incrementAndGet();
            _pair.physicalMap.clear();
          }
        }
      );
      myManager.registerRunnableToRunOnAnyChange(
        new Runnable() {
          public void run() {
            myClearCount.incrementAndGet();
            _pair.nonPhysicalMap.clear();
          }
        }
      );
    }
    return forPhysical ? pair.physicalMap : pair.nonPhysicalMap;
  }

  public static class MapPair<K,V>{
    public final ConcurrentMap<K,V> physicalMap = new ConcurrentWeakHashMap<K, V>();
    public final ConcurrentMap<K,V> nonPhysicalMap = new ConcurrentWeakHashMap<K, V>();
  }
}
