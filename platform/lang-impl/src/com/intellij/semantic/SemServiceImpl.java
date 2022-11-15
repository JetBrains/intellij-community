// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.semantic;

import com.intellij.ProjectTopics;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

import static java.util.Collections.emptyList;

public final class SemServiceImpl extends SemService implements Disposable {
  private static final Key<CachedValue<IntObjectMap<List<SemElement>>>> SEM_CACHE_KEY = Key.create("SEM");
  private static final CachedValueProvider<IntObjectMap<List<SemElement>>> SEM_CACHE_PROVIDER = () ->
    Result.create(createSemCache(), PsiModificationTracker.MODIFICATION_COUNT);

  private final Object lock = ObjectUtils.sentinel(getClass().getName());
  private volatile MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> producers;
  private final Project project;
  private final CachedValuesManager myCVManager;

  public SemServiceImpl(Project project) {
    this.project = project;
    myCVManager = CachedValuesManager.getManager(project);
    SemContributor.EP_NAME.addChangeListener(() -> producers = null, project);

    MessageBusConnection messageBusConnection = project.getMessageBus().connect(this);
    messageBusConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        producers = null;
      }
    });
    messageBusConnection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void exitDumbMode() {
        producers = null;
      }
    });
  }

  private MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> collectProducers() {
    MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> map = new MultiMap<>();

    SemRegistrar registrar = new SemRegistrar() {
      @Override
      public <T extends SemElement> void registerSemProvider(
        SemKey<T> key,
        BiFunction<? super PsiElement, ? super ProcessingContext, ? extends Collection<T>> provider
      ) {
        map.putValue(key, provider::apply);
      }
    };

    SemContributor.EP_NAME.processWithPluginDescriptor((contributor, pluginDescriptor) -> {
      SemContributor semContributor;
      try {
        semContributor = project.instantiateClass(contributor.implementation, pluginDescriptor);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (ExtensionNotApplicableException e) {
        return;
      }
      catch (Exception e) {
        Logger.getInstance(SemServiceImpl.class).error(e);
        return;
      }

      if (semContributor.isAvailable(project)) {
        semContributor.registerSemProviders(registrar, project);
      }
    });

    return map;
  }

  @Override
  public @NotNull <T extends SemElement> List<T> getSemElements(@NotNull SemKey<T> key, @NotNull PsiElement psi) {
    IntObjectMap<List<SemElement>> chunk = myCVManager.getCachedValue(psi, SEM_CACHE_KEY, SEM_CACHE_PROVIDER, false);
    List<T> cached = findCached(key, chunk);
    return cached != null ? cached : createSemElements(key, psi, chunk);
  }

  @SuppressWarnings("unchecked")
  private @NotNull <T extends SemElement> List<T> createSemElements(@NotNull SemKey<T> key, @NotNull PsiElement psi,
                                                                    IntObjectMap<List<SemElement>> chunk) {
    MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> currentProducers = ensureInitialized();

    RecursionGuard.StackStamp stamp = RecursionManager.markStack();

    Set<T> result = null;
    Map<SemKey<?>, List<SemElement>> map = null;

    ProcessingContext processingContext = new ProcessingContext();
    for (SemKey<?> each : key.getInheritors()) {
      List<SemElement> list = createSemElements(currentProducers, each, psi, processingContext);
      if (map == null) {
        map = new HashMap<>();
        result = new LinkedHashSet<>();
      }

      map.put(each, list);
      result.addAll((List<T>)list);
    }

    if (map != null && stamp.mayCacheNow()) {
      for (SemKey<?> semKey : map.keySet()) {
        putSemElements(chunk, semKey, map.get(semKey));
      }
    }

    if (result == null || result.isEmpty()) {
      return emptyList();
    }

    return List.copyOf(result);
  }

  private MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> ensureInitialized() {
    MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> current = producers;
    if (current != null) {
      return current;
    }

    synchronized (lock) {
      current = producers;
      if (current != null) {
        return current;
      }

      MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> newProducers = collectProducers();
      producers = newProducers;
      return newProducers;
    }
  }

  private static @NotNull List<SemElement> createSemElements(
    MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> producers,
    SemKey<?> key, PsiElement psi, ProcessingContext processingContext
  ) {
    List<SemElement> result = null;
    Collection<BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> functions = producers.get(key);
    if (!functions.isEmpty()) {
      for (BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>> producer : functions) {
        Collection<? extends SemElement> elements = producer.apply(psi, processingContext);
        if (elements != null && !elements.isEmpty()) {
          if (result == null) result = new SmartList<>();
          ContainerUtil.addAllNotNull(result, elements);
        }
      }
    }
    return result == null || result.isEmpty() ? emptyList() : result;
  }

  @Override
  public void dispose() {
  }

  @SuppressWarnings("unchecked")
  private static @Nullable <T extends SemElement> List<T> findCached(SemKey<T> key, IntObjectMap<List<SemElement>> chunk) {
    List<T> singleList = null;
    LinkedHashSet<T> result = null;
    List<SemKey<?>> inheritors = key.getInheritors();
    for (int i = 0; i < inheritors.size(); i++) {
      List<T> cached = (List<T>)getSemElements(chunk, inheritors.get(i));
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

    return List.copyOf(result);
  }

  private static IntObjectMap<List<SemElement>> createSemCache() {
    return ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  }

  private static List<SemElement> getSemElements(IntObjectMap<List<SemElement>> semCache, SemKey<?> key) {
    return semCache.get(key.getUniqueId());
  }

  private static void putSemElements(IntObjectMap<List<SemElement>> semCache, @NotNull SemKey<?> key, @NotNull List<SemElement> elements) {
    semCache.put(key.getUniqueId(), elements);
  }
}
