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
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class ResolveCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.ResolveCache");
  private final ConcurrentMap[] myMaps = new ConcurrentMap[2*2*2]; //boolean physical, boolean incompleteCode, boolean isPoly
  private final RecursionGuard myGuard = RecursionManager.createGuard("resolveCache");

  public static ResolveCache getInstance(Project project) {
    ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly
    return ServiceManager.getService(project, ResolveCache.class);
  }

  public interface AbstractResolver<TRef extends PsiReference,TResult> {
    TResult resolve(@NotNull TRef ref, boolean incompleteCode);
  }
  public interface PolyVariantResolver<T extends PsiPolyVariantReference> extends AbstractResolver<T,ResolveResult[]> {
    @Override
    @NotNull
    ResolveResult[] resolve(@NotNull T t, boolean incompleteCode);
  }

  public interface Resolver extends AbstractResolver<PsiReference,PsiElement>{
  }

  public ResolveCache(@NotNull MessageBus messageBus) {
    for (int i = 0; i < myMaps.length; i++) {
      myMaps[i] = createWeakMap();
    }
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

  private static <K,V> ConcurrentWeakHashMap<K, V> createWeakMap() {
    return new ConcurrentWeakHashMap<K,V>(100, 0.75f, Runtime.getRuntime().availableProcessors(), ContainerUtil.<K>canonicalStrategy());
  }

  public void clearCache(boolean isPhysical) {
    int startIndex = isPhysical ? 0 : 1;
    for (int i=startIndex;i<2;i++)for (int j=0;j<2;j++)for (int k=0;k<2;k++) myMaps[i*4+j*2+k].clear();
  }

  @Nullable
  private <TRef extends PsiReference, TResult> TResult resolve(@NotNull final TRef ref,
                                                               @NotNull final AbstractResolver<TRef, TResult> resolver,
                                                               boolean needToPreventRecursion,
                                                               final boolean incompleteCode,
                                                               boolean isPoly,
                                                               boolean isPhysical) {
    ProgressIndicatorProvider.checkCanceled();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    ConcurrentMap<TRef, Getter<TResult>> map = getMap(isPhysical, incompleteCode, isPoly);
    Getter<TResult> reference = map.get(ref);
    TResult result = reference == null ? null : reference.get();
    if (result != null) {
      return result;
    }

    RecursionGuard.StackStamp stamp = myGuard.markStack();
    result = needToPreventRecursion ? myGuard.doPreventingRecursion(Trinity.create(ref, incompleteCode, isPoly), true, new Computable<TResult>() {
      @Override
      public TResult compute() {
        return resolver.resolve(ref, incompleteCode);
      }
    }) : resolver.resolve(ref, incompleteCode);
    PsiElement element = result instanceof ResolveResult ? ((ResolveResult)result).getElement() : null;
    LOG.assertTrue(element == null || element.isValid(), result);

    if (stamp.mayCacheNow()) {
      cache(ref, map, result, isPoly);
    }
    return result;
  }

  @NotNull
  public <T extends PsiPolyVariantReference> ResolveResult[] resolveWithCaching(@NotNull T ref,
                                                                                @NotNull PolyVariantResolver<T> resolver,
                                                                                boolean needToPreventRecursion,
                                                                                boolean incompleteCode) {
    return resolveWithCaching(ref, resolver, needToPreventRecursion, incompleteCode, ref.getElement().getContainingFile());
  }
  @NotNull
  public <T extends PsiPolyVariantReference> ResolveResult[] resolveWithCaching(@NotNull T ref,
                                                                                @NotNull PolyVariantResolver<T> resolver,
                                                                                boolean needToPreventRecursion,
                                                                                boolean incompleteCode,
                                                                                @NotNull PsiFile containingFile) {
    ResolveResult[] result = resolve(ref, resolver, needToPreventRecursion, incompleteCode, true, containingFile.isPhysical());
    return result == null ? ResolveResult.EMPTY_ARRAY : result;
  }

  @Nullable
  public <T extends PsiPolyVariantReference> ResolveResult[] getCachedResults(@NotNull T ref, boolean physical, boolean incompleteCode, boolean isPoly) {
    Map<T, Getter<ResolveResult[]>> map = getMap(physical, incompleteCode, isPoly);
    Getter<ResolveResult[]> reference = map.get(ref);
    return reference == null ? null : reference.get();
  }

  @Nullable
  public <TRef extends PsiReference, TResult>
         TResult resolveWithCaching(@NotNull TRef ref,
                                    @NotNull AbstractResolver<TRef, TResult> resolver,
                                    boolean needToPreventRecursion,
                                    boolean incompleteCode) {
    return resolve(ref, resolver, needToPreventRecursion, incompleteCode, false, ref.getElement().isPhysical());
  }

  private <TRef extends PsiReference,TResult> ConcurrentMap<TRef, Getter<TResult>> getMap(boolean physical, boolean incompleteCode, boolean isPoly) {
    //noinspection unchecked
    return myMaps[(physical ? 0 : 1)*4 + (incompleteCode ? 0 : 1)*2 + (isPoly ? 0 : 1)];
  }

  private static class SoftGetter<T> extends SoftReference<T> implements Getter<T> {
    public SoftGetter(T referent) {
      super(referent);
    }
  }
  private static final Getter<ResolveResult[]> EMPTY_POLY_RESULT = new StaticGetter<ResolveResult[]>(ResolveResult.EMPTY_ARRAY);
  private static final Getter<Object> NULL_RESULT = new StaticGetter<Object>(null);
  private static <TRef extends PsiReference, TResult> void cache(@NotNull TRef ref,
                                                                 @NotNull ConcurrentMap<TRef, Getter<TResult>> map,
                                                                 TResult result,
                                                                 boolean isPoly) {
    // optimization: less contention
    Getter<TResult> cached = map.get(ref);
    if (cached != null && cached.get() == result) {
      return;
    }
    if (result == null) {
      // no use in creating SoftReference to null
      //noinspection unchecked
      cached = (Getter<TResult>)NULL_RESULT;
    }
    else if (isPoly && ((Object[])result).length == 0) {
      // no use in creating SoftReference to empty array
      //noinspection unchecked
      cached = result.getClass() == ResolveResult[].class ? (Getter<TResult>)EMPTY_POLY_RESULT : new StaticGetter<TResult>(result);
    }
    else {
      cached = new SoftGetter<TResult>(result);
    }
    map.put(ref, cached);
  }
}
