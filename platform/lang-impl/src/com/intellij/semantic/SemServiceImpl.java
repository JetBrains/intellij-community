// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.semantic;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

import static java.util.Collections.emptyList;

/**
 * @author peter
 */
public final class SemServiceImpl extends SemService {
  private static final Logger LOG = Logger.getInstance(SemServiceImpl.class);

  private final Object myLock = ObjectUtils.sentinel(getClass().getName());
  private volatile MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> myProducers;
  private final Project myProject;
  private final CachedValuesManager myCVManager;

  public SemServiceImpl(Project project) {
    myProject = project;
    myCVManager = CachedValuesManager.getManager(myProject);
    SemContributor.EP_NAME.addChangeListener(() -> myProducers = null, project);
  }

  private MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> collectProducers() {
    MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> map = new MultiMap<>();

    SemRegistrar registrar = new SemRegistrar() {
      @Override
      public <T extends SemElement> void registerSemProvider(
        SemKey<T> key,
        BiFunction<? super PsiElement, ? super ProcessingContext, ? extends Collection<T>> provider) {

        map.putValue(key, provider::apply);
      }
    };

    SemContributor.EP_NAME.processWithPluginDescriptor((contributor, pluginDescriptor) -> {
      SemContributor semContributor;
      try {
        semContributor = myProject.instantiateClass(contributor.implementation, pluginDescriptor);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (ExtensionNotApplicableException e) {
        return;
      }
      catch (Exception e) {
        LOG.error(e);
        return;
      }
      semContributor.registerSemProviders(registrar, myProject);
    });

    return map;
  }

  @NotNull
  @Override
  public <T extends SemElement> List<T> getSemElements(@NotNull SemKey<T> key, @NotNull PsiElement psi) {
    SemCacheChunk chunk = myCVManager.getCachedValue((UserDataHolder)psi, () ->
      Result.create(new SemCacheChunk(), PsiModificationTracker.MODIFICATION_COUNT));
    List<T> cached = findCached(key, chunk);
    return cached != null ? cached : createSemElements(key, psi, chunk);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private <T extends SemElement> List<T> createSemElements(@NotNull SemKey<T> key, @NotNull PsiElement psi, SemCacheChunk chunk) {
    ensureInitialized();

    RecursionGuard.StackStamp stamp = RecursionManager.markStack();

    LinkedHashSet<T> result = new LinkedHashSet<>();
    Map<SemKey<?>, List<SemElement>> map = new HashMap<>();

    ProcessingContext processingContext = new ProcessingContext();
    for (SemKey<?> each : key.getInheritors()) {
      List<SemElement> list = createSemElements(each, psi, processingContext);
      map.put(each, list);
      result.addAll((List<T>)list);
    }

    if (stamp.mayCacheNow()) {
      for (SemKey<?> semKey : map.keySet()) {
        chunk.putSemElements(semKey, map.get(semKey));
      }
    }

    return new ArrayList<>(result);
  }

  private void ensureInitialized() {
    if (myProducers == null) {
      synchronized (myLock) {
        if (myProducers == null) {
          myProducers = collectProducers();
        }
      }
    }
  }

  @NotNull
  private List<SemElement> createSemElements(SemKey<?> key, PsiElement psi, ProcessingContext processingContext) {
    List<SemElement> result = null;
    Collection<BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> functions = myProducers.get(key);
    if (!functions.isEmpty()) {
      for (BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>> producer : functions) {
        Collection<? extends SemElement> elements = producer.apply(psi, processingContext);
        if (elements != null) {
          if (result == null) result = new SmartList<>();
          ContainerUtil.addAllNotNull(result, elements);
        }
      }
    }
    return result == null ? emptyList() : Collections.unmodifiableList(result);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private static <T extends SemElement> List<T> findCached(SemKey<T> key, SemCacheChunk chunk) {
    List<T> singleList = null;
    LinkedHashSet<T> result = null;
    List<SemKey<?>> inheritors = key.getInheritors();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < inheritors.size(); i++) {
      List<T> cached = (List<T>)chunk.getSemElements(inheritors.get(i));
      if (cached == null) {
        return null;
      }

      if (cached != Collections.<T>emptyList()) {
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

      return emptyList();
    }

    return new ArrayList<>(result);
  }

  private static class SemCacheChunk {
    private final IntObjectMap<List<SemElement>> map = ConcurrentCollectionFactory.createConcurrentIntObjectMap();

    List<SemElement> getSemElements(SemKey<?> key) {
      return map.get(key.getUniqueId());
    }

    void putSemElements(@NotNull SemKey<?> key, @NotNull List<SemElement> elements) {
      map.put(key.getUniqueId(), elements);
    }
  }
}
