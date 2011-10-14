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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.messages.MessageBus;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ResolveCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.ResolveCache");
  private final Map<PsiPolyVariantReference,Reference<ResolveResult[]>>[] myPolyVariantResolveMaps = new Map[4];
  private final Map<PsiReference,Reference>[] myResolveMaps = new Map[4];
  private final AtomicInteger myClearCount = new AtomicInteger(0);
  private final RecursionGuard myGuard = RecursionManager.createGuard("resolveCache");

  public static ResolveCache getInstance(Project project) {
    ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly
    return ServiceManager.getService(project, ResolveCache.class);
  }

  public interface AbstractResolver<TRef extends PsiReference,TResult> {
    TResult resolve(TRef ref, boolean incompleteCode);
  }
  public interface PolyVariantResolver<T extends PsiPolyVariantReference> extends AbstractResolver<T,ResolveResult[]> {
  }

  public interface Resolver extends AbstractResolver<PsiReference,PsiElement>{
  }

  public ResolveCache(@Nullable MessageBus messageBus) {
    myPolyVariantResolveMaps[0] = createWeakMap();
    myPolyVariantResolveMaps[1] = createWeakMap();
    myResolveMaps[0] = createWeakMap();
    myResolveMaps[1] = createWeakMap();

    myPolyVariantResolveMaps[2] = createWeakMap();
    myPolyVariantResolveMaps[3] = createWeakMap();
    myResolveMaps[2] = createWeakMap();
    myResolveMaps[3] = createWeakMap();

    if (messageBus != null) {
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
  }

  private static <K,V> ConcurrentWeakHashMap<K, V> createWeakMap() {
    return new ConcurrentWeakHashMap<K,V>(100, 0.75f, Runtime.getRuntime().availableProcessors(), TObjectHashingStrategy.CANONICAL);
  }

  public void clearCache(boolean isPhysical) {
    myClearCount.incrementAndGet();
    if (isPhysical) {
      myPolyVariantResolveMaps[0].clear();  //physical complete
      myPolyVariantResolveMaps[1].clear();  //physical incomplete
      myResolveMaps[0].clear();             //physical complete
      myResolveMaps[1].clear();             //physical incomplete
    }

    myPolyVariantResolveMaps[2].clear();   //nonphysical complete
    myPolyVariantResolveMaps[3].clear();   //nonphysical incomplete
    myResolveMaps[2].clear();              //nonphysical complete
    myResolveMaps[3].clear();              //nonphysical incomplete
  }

  @Nullable
  private <TRef extends PsiReference, TResult> TResult resolve(@NotNull final TRef ref,
                                                               @NotNull final AbstractResolver<TRef, TResult> resolver,
                                                               @NotNull Map<? super TRef,Reference<TResult>>[] maps,
                                                               boolean needToPreventRecursion,
                                                               final boolean incompleteCode, boolean poly) {
    ProgressIndicatorProvider.checkCanceled();
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
    result = needToPreventRecursion ? myGuard.doPreventingRecursion(Trinity.create(ref, incompleteCode, poly), true, computable) : computable.compute();
    if (stamp.mayCacheNow()) {
      cache(ref, result, maps, physical, incompleteCode, clearCountOnStart);
    }
    return result;
  }

   public <T extends PsiPolyVariantReference> ResolveResult[] resolveWithCaching(@NotNull T ref,
                                                                                 @NotNull PolyVariantResolver<T> resolver,
                                                                                 boolean needToPreventRecursion,
                                                                                 boolean incompleteCode) {
    ResolveResult[] result = resolve(ref, resolver, myPolyVariantResolveMaps, needToPreventRecursion, incompleteCode, true);
    return result == null ? ResolveResult.EMPTY_ARRAY : result;
  }

  public PsiElement resolveWithCaching(@NotNull PsiReference ref,
                                       @NotNull Resolver resolver,
                                       boolean needToPreventRecursion,
                                       boolean incompleteCode) {
    return resolve(ref, resolver, (Map[]) myResolveMaps, needToPreventRecursion, incompleteCode, false);
  }

  @Nullable
  public <TRef extends PsiReference, TResult>TResult resolveWithCaching(@NotNull TRef ref,
                                       @NotNull AbstractResolver<TRef, TResult> resolver,
                                       boolean needToPreventRecursion,
                                       boolean incompleteCode) {
    return (TResult)resolve(ref, resolver, (Map[]) myResolveMaps, needToPreventRecursion, incompleteCode, false);
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
    PsiElement element = result instanceof ResolveResult ? ((ResolveResult)result).getElement() : null;
    LOG.assertTrue(element == null || element.isValid(), result);

    int index = getIndex(physical, incompleteCode);
    // optimization: lower contention
    Map<? super TRef, Reference<TResult>> map = maps[index];
    Reference<TResult> cached = map.get(ref);
    if (cached != null && cached.get() == result) {
      return;
    }

    map.put(ref, new SoftReference<TResult>(result/*, myQueue*/));
  }
}
