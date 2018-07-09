/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.semantic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author peter
 */
@SuppressWarnings({"unchecked"})
public class SemServiceImpl extends SemService{
  private final AtomicReference<ConcurrentMap<PsiElement, SemCacheChunk>> myCache = new AtomicReference<>();
  private volatile  MultiMap<SemKey, NullableFunction<PsiElement, Collection<? extends SemElement>>> myProducers;
  private final Project myProject;

  private boolean myBulkChange = false;
  private final AtomicInteger myCreatingSem = new AtomicInteger(0);

  public SemServiceImpl(Project project, PsiManager psiManager) {
    myProject = project;
    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(PsiModificationTracker.TOPIC, new PsiModificationTracker.Listener() {
      @Override
      public void modificationCountChanged() {
        if (!isInsideAtomicChange()) {
          clearCache();
        }
      }
    });

    ((PsiManagerEx)psiManager).registerRunnableToRunOnChange(() -> {
      if (!isInsideAtomicChange()) {
        clearCache();
      }
    });


    LowMemoryWatcher.register(() -> {
      if (myCreatingSem.get() == 0) {
        clearCache();
      }
      //System.out.println("SemService cache flushed");
    }, project);
  }

  private MultiMap<SemKey, NullableFunction<PsiElement, Collection<? extends SemElement>>> collectProducers() {
    final MultiMap<SemKey, NullableFunction<PsiElement, Collection<? extends SemElement>>> map = MultiMap.createSmart();

    final SemRegistrar registrar = new SemRegistrar() {
      @Override
      public <T extends SemElement, V extends PsiElement> void registerSemElementProvider(SemKey<T> key,
                                                                                          final ElementPattern<? extends V> place,
                                                                                          final NullableFunction<V, T> provider) {
        map.putValue(key, element -> {
          if (place.accepts(element)) {
            return Collections.singleton(provider.fun((V)element));
          }
          return null;
        });
      }

      @Override
      public <T extends SemElement, V extends PsiElement> void registerRepeatableSemElementProvider(SemKey<T> key,
                                                                                                    ElementPattern<? extends V> place,
                                                                                                    NullableFunction<V, Collection<T>> provider) {
        map.putValue(key, element -> {
          if (place.accepts(element)) {
            return provider.fun((V)element);
          }
          return null;
        });
      }
    };

    for (SemContributorEP contributor : myProject.getExtensions(SemContributor.EP_NAME)) {
      contributor.registerSemProviders(myProject.getPicoContainer(), registrar);
    }

    return map;
  }

  @Override
  public void clearCache() {
    myCache.set(null);
  }

  @Override
  public void performAtomicChange(@NotNull Runnable change) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final boolean oldValue = myBulkChange;
    myBulkChange = true;
    try {
      change.run();
    }
    finally {
      myBulkChange = oldValue;
      if (!oldValue) {
        clearCache();
      }
    }
  }

  @Override
  public boolean isInsideAtomicChange() {
    return myBulkChange;
  }

  @Override
  @Nullable
  public <T extends SemElement> List<T> getSemElements(final SemKey<T> key, @NotNull final PsiElement psi) {
    List<T> cached = _getCachedSemElements(key, true, psi);
    if (cached != null) {
      return cached;
    }

    ensureInitialized();

    RecursionGuard.StackStamp stamp = RecursionManager.createGuard("semService").markStack();

    LinkedHashSet<T> result = new LinkedHashSet<>();
    final Map<SemKey, List<SemElement>> map = new THashMap<>();
    for (final SemKey each : key.getInheritors()) {
      List<SemElement> list = createSemElements(each, psi);
      map.put(each, list);
      result.addAll((List<T>)list);
    }

    if (stamp.mayCacheNow()) {
      final SemCacheChunk persistent = getOrCreateChunk(psi);
      for (SemKey semKey : map.keySet()) {
        persistent.putSemElements(semKey, map.get(semKey));
      }
    }

    return new ArrayList<>(result);
  }

  private void ensureInitialized() {
    if (myProducers == null) {
      myProducers = collectProducers();
    }
  }

  @NotNull
  private List<SemElement> createSemElements(SemKey key, PsiElement psi) {
    List<SemElement> result = null;
    Collection<NullableFunction<PsiElement, Collection<? extends SemElement>>> functions = myProducers.get(key);
    if (!functions.isEmpty()) {
      for (final NullableFunction<PsiElement, Collection<? extends SemElement>> producer : functions) {
        myCreatingSem.incrementAndGet();
        try {
          final Collection<? extends SemElement> elements = producer.fun(psi);
          if (elements != null) {
            if (result == null) result = new SmartList<>();
            ContainerUtil.addAllNotNull(result, elements);
          }
        }
        finally {
          myCreatingSem.decrementAndGet();
        }
      }
    }
    return result == null ? Collections.emptyList() : Collections.unmodifiableList(result);
  }

  @Override
  @Nullable
  public <T extends SemElement> List<T> getCachedSemElements(SemKey<T> key, @NotNull PsiElement psi) {
    return _getCachedSemElements(key, false, psi);
  }

  @Nullable
  private <T extends SemElement> List<T> _getCachedSemElements(SemKey<T> key, boolean paranoid, final PsiElement element) {
    final SemCacheChunk chunk = obtainChunk(element);
    if (chunk == null) return null;

    List<T> singleList = null;
    LinkedHashSet<T> result = null;
    final List<SemKey> inheritors = key.getInheritors();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < inheritors.size(); i++) {
      List<T> cached = (List<T>)chunk.getSemElements(inheritors.get(i));

      if (cached == null && paranoid) {
        return null;
      }

      if (cached != null && cached != Collections.<T>emptyList()) {
        if (singleList == null) {
          singleList = cached;
          continue;
        }

        if (result == null) {
          result = new LinkedHashSet<>(singleList);
        }
        result.addAll(cached);
      }
    }


    if (result == null) {
      if (singleList != null) {
        return singleList;
      }

      return Collections.emptyList();
    }

    return new ArrayList<>(result);
  }

  @Nullable
  private SemCacheChunk obtainChunk(@Nullable PsiElement root) {
    ConcurrentMap<PsiElement, SemCacheChunk> map = myCache.get();
    return map == null ? null : map.get(root);
  }

  @Override
  public <T extends SemElement> void setCachedSemElement(SemKey<T> key, @NotNull PsiElement psi, @Nullable T semElement) {
    getOrCreateChunk(psi).putSemElements(key, ContainerUtil.createMaybeSingletonList(semElement));
  }

  private SemCacheChunk getOrCreateChunk(final PsiElement element) {
    SemCacheChunk chunk = obtainChunk(element);
    if (chunk == null) {
      ConcurrentMap<PsiElement, SemCacheChunk> map = myCache.get();
      if (map == null) {
        map = ConcurrencyUtil.cacheOrGet(myCache, ContainerUtil.createConcurrentWeakKeySoftValueMap());
      }
      chunk = ConcurrencyUtil.cacheOrGet(map, element, new SemCacheChunk());
    }
    return chunk;
  }

  private static class SemCacheChunk {
    private final IntObjectMap<List<SemElement>> map = ContainerUtil.createConcurrentIntObjectMap();

    public List<SemElement> getSemElements(SemKey<?> key) {
      return map.get(key.getUniqueId());
    }

    public void putSemElements(SemKey<?> key, List<SemElement> elements) {
      map.put(key.getUniqueId(), elements);
    }

    @Override
    public int hashCode() {
      return 0; // ConcurrentWeakKeySoftValueHashMap.SoftValue requires hashCode, and this is faster than identityHashCode
    }
  }

}
