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
package com.intellij.semantic;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
@SuppressWarnings({"unchecked"})
public class SemServiceImpl extends SemService{
  private static final Comparator<SemKey> KEY_COMPARATOR = new Comparator<SemKey>() {
    public int compare(SemKey o1, SemKey o2) {
      return o2.getUniqueId() - o1.getUniqueId();
    }
  };
  private final ConcurrentMap<PsiElement, ConcurrentMap<SemKey, List<SemElement>>> myCache = new ConcurrentHashMap<PsiElement, ConcurrentMap<SemKey, List<SemElement>>>();
  private final Map<SemKey, Collection<NullableFunction<PsiElement, ? extends SemElement>>> myProducers = new THashMap<SemKey, Collection<NullableFunction<PsiElement,? extends SemElement>>>();
  private final MultiMap<SemKey, SemKey> myInheritors = new MultiMap<SemKey, SemKey>();
  private final Project myProject;

  private volatile boolean myInitialized = false;
  private boolean myBulkChange = false;

  public SemServiceImpl(Project project, PsiManager psiManager) {
    myProject = project;
    project.getMessageBus().connect().subscribe(ProjectTopics.MODIFICATION_TRACKER, new PsiModificationTracker.Listener() {
      public void modificationCountChanged() {
        if (!isInsideAtomicChange()) {
          clearCache();
        }
      }
    });
    ((PsiManagerEx)psiManager).registerRunnableToRunOnChange(new Runnable() {
      public void run() {
        if (!isInsideAtomicChange()) {
          clearCache();
        }
      }
    });
  }

  public void clearCache() {
    myCache.clear();
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

  private void ensureInitialized() {
    if (myInitialized) return;

    final MultiMap<SemKey, NullableFunction<PsiElement, ? extends SemElement>> map = new MultiMap<SemKey, NullableFunction<PsiElement, ? extends SemElement>>();

    final SemRegistrar registrar = new SemRegistrar() {
      public <T extends SemElement, V extends PsiElement> void registerSemElementProvider(SemKey<T> key,
                                                                                   final ElementPattern<? extends V> place,
                                                                                   final NullableFunction<V, T> provider) {
        map.putValue(key, new NullableFunction<PsiElement, SemElement>() {
          public SemElement fun(PsiElement element) {
            if (place.accepts(element)) {
              return provider.fun((V)element);
            }
            return null;
          }
        });
      }
    };
    for (SemContributor contributor : myProject.getExtensions(SemContributor.EP_NAME)) {
      contributor.registerSemProviders(registrar);
    }

    synchronized (myCache) {
      if (myInitialized) return;

      assert myProducers.isEmpty();
      assert myInheritors.isEmpty();

      SemKey[] allKeys = map.keySet().toArray(new SemKey[map.size()]);
      for (final SemKey key : allKeys) {
        myProducers.put(key, map.get(key));
      }
      ContainerUtil.process(allKeys, new Processor<SemKey>() {
        public boolean process(SemKey key) {
          myInheritors.putValue(key, key);
          for (SemKey parent : key.getSupers()) {
            myInheritors.putValue(parent, key);
            process(parent);
          }
          return true;
        }
      });
      for (final SemKey each : myInheritors.keySet()) {
        final List<SemKey> inheritors = new ArrayList<SemKey>(myInheritors.get(each));
        Collections.sort(inheritors, KEY_COMPARATOR);
        myInheritors.put(each, inheritors);
      }

      myInitialized = true;
    }
  }

  @Nullable
  public <T extends SemElement> List<T> getSemElements(SemKey<T> key, @NotNull PsiElement psi) {
    ensureInitialized();

    List<T> cached = _getCachedSemElements(key, psi, true);
    if (cached != null) {
      return cached;
    }

    final Map<SemKey, List<SemElement>> map = new THashMap<SemKey, List<SemElement>>();
    LinkedHashSet<T> result = null;
    for (final SemKey each : myInheritors.get(key)) {
      List<SemElement> list = createSemElements(each, psi);
      map.put(each, list);
      if (!list.isEmpty()) {
        if (result == null) result = new LinkedHashSet<T>();
        result.addAll((List<T>)list);
      }
    }
    final ConcurrentMap<SemKey, List<SemElement>> persistent = cacheOrGetMap(psi);
    for (SemKey semKey : map.keySet()) {
      persistent.putIfAbsent(semKey, map.get(semKey));
    }
    return result == null ? Collections.<T>emptyList() : new ArrayList<T>(result);
  }

  @NotNull 
  private List<SemElement> createSemElements(SemKey key, PsiElement psi) {
    List<SemElement> result = null;
    final Collection<NullableFunction<PsiElement, ? extends SemElement>> producers = myProducers.get(key);
    if (producers != null && !producers.isEmpty()) {
      for (final NullableFunction<PsiElement, ? extends SemElement> producer : producers) {
        final SemElement element = producer.fun(psi);
        if (element != null) {
          if (result == null) result = new SmartList<SemElement>();
          result.add(element);
        }  
      }
    }
    return result == null ? Collections.<SemElement>emptyList() : Collections.unmodifiableList(result);
  }

  @Nullable
  public <T extends SemElement> List<T> getCachedSemElements(SemKey<T> key, @NotNull PsiElement psi) {
    return _getCachedSemElements(key, psi, false);
  }

  @Nullable
  private <T extends SemElement> List<T> _getCachedSemElements(SemKey<T> key, PsiElement psi, boolean paranoid) {
    final ConcurrentMap<SemKey, List<SemElement>> map = myCache.get(psi);
    if (map == null) return null;

    List<T> singleList = null;
    LinkedHashSet<T> result = null;
    final List<SemKey> inheritors = (List<SemKey>)myInheritors.get(key);
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < inheritors.size(); i++) {
      List<T> cached = (List<T>)map.get(inheritors.get(i));

      if (cached == null && paranoid) {
        return null;
      }

      if (cached != null && cached != Collections.<T>emptyList()) {
        if (singleList == null) {
          singleList = cached;
          continue;
        }

        if (result == null) {
          result = new LinkedHashSet<T>(singleList);
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

    return new ArrayList<T>(result);
  }

  public <T extends SemElement> void setCachedSemElement(SemKey<T> key, @NotNull PsiElement psi, @Nullable T semElement) {
    cacheOrGetMap(psi).put(key, ContainerUtil.<SemElement>createMaybeSingletonList(semElement));
  }

  @Override
  public <T extends SemElement> void clearCachedSemElements(@NotNull PsiElement psi) {
    myCache.remove(psi);
  }

  private ConcurrentMap<SemKey, List<SemElement>> cacheOrGetMap(PsiElement psi) {
    ConcurrentMap<SemKey, List<SemElement>> map = myCache.get(psi);
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(myCache, psi, new ConcurrentHashMap<SemKey, List<SemElement>>());
    }
    return map;
  }

}
