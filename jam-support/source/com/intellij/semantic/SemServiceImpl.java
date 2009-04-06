/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
@SuppressWarnings({"unchecked"})
public class SemServiceImpl extends SemService{
  private final ConcurrentMap<PsiElement, ConcurrentMap<SemKey, List<SemElement>>> myCache = new ConcurrentHashMap<PsiElement, ConcurrentMap<SemKey, List<SemElement>>>();
  private final SortedMap<SemKey, Collection<NullableFunction<PsiElement, ? extends SemElement>>> myProducers = new TreeMap<SemKey, Collection<NullableFunction<PsiElement,? extends SemElement>>>(new Comparator<SemKey>() {
    public int compare(SemKey o1, SemKey o2) {
      return o2.getUniqueId() - o1.getUniqueId();
    }
  });
  private volatile boolean myInitialized = false;
  private final Project myProject;
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
      for (final SemKey key : map.keySet()) {
        myProducers.put(key, map.get(key));
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

    final ConcurrentMap<SemKey, List<SemElement>> map = cacheOrGetMap(psi);
    LinkedHashSet<T> result = null;
    for (final SemKey each : myProducers.keySet()) {
      if (each.isKindOf(key)) {
        List<SemElement> list = ConcurrencyUtil.cacheOrGet(map, each, createSemElements(each, psi));
        if (!list.isEmpty()) {
          if (result == null) result = new LinkedHashSet<T>();
          result.addAll((List<T>)list);
        }
      }
    }
    return result == null ? Collections.<T>emptyList() : new ArrayList<T>(result);
  }

  @NotNull 
  private List<SemElement> createSemElements(SemKey key, PsiElement psi) {
    List<SemElement> result = null;
    final Collection<NullableFunction<PsiElement, ? extends SemElement>> producers = myProducers.get(key);
    if (!producers.isEmpty()) {
      for (final NullableFunction<PsiElement, ? extends SemElement> producer : producers) {
        final SemElement element = producer.fun(psi);
        if (element != null) {
          if (result == null) result = new SmartList<SemElement>();
          result.add(element);
        }  
      }
    }
    return result == null ? Collections.<SemElement>emptyList() : result;
  }

  @Nullable
  public <T extends SemElement> List<T> getCachedSemElements(SemKey<T> key, @NotNull PsiElement psi) {
    return _getCachedSemElements(key, psi, false);
  }

  @Nullable
  private <T extends SemElement> List<T> _getCachedSemElements(SemKey<T> key, PsiElement psi, boolean paranoid) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final ConcurrentMap<SemKey, List<SemElement>> map = myCache.get(psi);
    if (map == null) return null;

    LinkedHashSet<T> result = null;
    boolean allComputed = true;
    for (final SemKey each : myProducers.keySet()) {
      if (each.isKindOf(key)) {
        List<T> cached = (List<T>)map.get(each);
        allComputed &= cached != null;
        if (cached != null && !cached.isEmpty()) {
          if (result == null) result = new LinkedHashSet<T>();
          result.addAll(cached);
        }
      }
    }


    if (!allComputed && paranoid) {
      return null;
    }

    if (result == null) {
      return Collections.emptyList();
    }

    return new ArrayList<T>(result);
  }

  public <T extends SemElement> void setCachedSemElement(SemKey<T> key, @NotNull PsiElement psi, @Nullable T semElement) {
    cacheOrGetMap(psi).put(key, ContainerUtil.<SemElement>createMaybeSingletonList(semElement));
  }
                    
  private ConcurrentMap<SemKey, List<SemElement>> cacheOrGetMap(PsiElement psi) {
    ConcurrentMap<SemKey, List<SemElement>> map = myCache.get(psi);
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(myCache, psi, new ConcurrentHashMap<SemKey, List<SemElement>>());
    }
    return map;
  }

}
