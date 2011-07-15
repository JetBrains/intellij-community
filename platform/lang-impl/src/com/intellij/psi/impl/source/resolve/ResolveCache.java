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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ResolveCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.ResolveCache");
  private final Map<PsiPolyVariantReference,Reference<ResolveResult[]>>[] myPolyVariantResolveMaps = new Map[4];
  private final Map<PsiReference,Reference<PsiElement>>[] myResolveMaps = new Map[4];
  private final AtomicInteger myClearCount = new AtomicInteger(0);
  private final RecursionGuard myGuard = RecursionManager.createGuard("resolveCache");

  public interface AbstractResolver<TRef extends PsiReference,TResult> {
    TResult resolve(TRef ref, boolean incompleteCode);
  }
  public interface PolyVariantResolver<T extends PsiPolyVariantReference> extends AbstractResolver<T,ResolveResult[]> {
  }

  public interface Resolver extends AbstractResolver<PsiReference,PsiElement>{
  }

  public ResolveCache(@NotNull MessageBus messageBus) {
    myPolyVariantResolveMaps[0] = new ConcurrentWeakHashMap<PsiPolyVariantReference,Reference<ResolveResult[]>>();
    myPolyVariantResolveMaps[1] = new ConcurrentWeakHashMap<PsiPolyVariantReference,Reference<ResolveResult[]>>();
    myResolveMaps[0] = new ConcurrentWeakHashMap<PsiReference, Reference<PsiElement>>();
    myResolveMaps[1] = new ConcurrentWeakHashMap<PsiReference, Reference<PsiElement>>();

    myPolyVariantResolveMaps[2] = new ConcurrentWeakHashMap<PsiPolyVariantReference, Reference<ResolveResult[]>>();
    myPolyVariantResolveMaps[3] = new ConcurrentWeakHashMap<PsiPolyVariantReference, Reference<ResolveResult[]>>();
    myResolveMaps[2] = new ConcurrentWeakHashMap<PsiReference, Reference<PsiElement>>();
    myResolveMaps[3] = new ConcurrentWeakHashMap<PsiReference, Reference<PsiElement>>();

    messageBus.connect().subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        clearCache(isPhysical);
      }

      @Override
      public void afterPsiChanged(boolean isPhysical) {
      }
    });
  }

  public void clearCache(boolean isPhysical) {
    myClearCount.incrementAndGet();
    if (isPhysical) {
      myPolyVariantResolveMaps[0].clear();
      myPolyVariantResolveMaps[1].clear();
      myResolveMaps[0].clear();
      myResolveMaps[1].clear();
    }

    myPolyVariantResolveMaps[2].clear();
    myPolyVariantResolveMaps[3].clear();
    myResolveMaps[2].clear();
    myResolveMaps[3].clear();
  }

  @Nullable
  private <TRef extends PsiReference, TResult> TResult resolve(@NotNull final TRef ref,
                                                               @NotNull final AbstractResolver<TRef, TResult> resolver,
                                                               @NotNull Map<? super TRef,Reference<TResult>>[] maps,
                                                               boolean needToPreventRecursion,
                                                               final boolean incompleteCode) {
    ProgressManager.checkCanceled();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    int clearCountOnStart = myClearCount.intValue();
    boolean physical = ref.getElement().isPhysical();
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

   public <T extends PsiPolyVariantReference> ResolveResult[] resolveWithCaching(@NotNull T ref,
                                                                                 @NotNull PolyVariantResolver<T> resolver,
                                                                                 boolean needToPreventRecursion,
                                                                                 boolean incompleteCode) {
    ResolveResult[] result = resolve(ref, resolver, myPolyVariantResolveMaps, needToPreventRecursion, incompleteCode);
    return result == null ? ResolveResult.EMPTY_ARRAY : result;
  }

  public PsiElement resolveWithCaching(@NotNull PsiReference ref,
                                       @NotNull Resolver resolver,
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
    PsiElement element = result instanceof ResolveResult ? ((ResolveResult)result).getElement() : null;
    LOG.assertTrue(element == null || element.isValid(), result);
  }
}
