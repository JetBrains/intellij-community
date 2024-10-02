// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.semantic;

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
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

import static com.intellij.concurrency.ConcurrentCollectionFactory.createConcurrentIntObjectMap;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@ApiStatus.Internal
public final class SemServiceImpl extends SemService implements Disposable {
  private static final Key<SemData> SEM_CACHE_KEY = Key.create("SEM");

  private final Object lock = ObjectUtils.sentinel(getClass().getName());
  private volatile State keysAndProducers;

  private final Project myProject;
  private final PsiModificationTracker myPsiModificationTracker;

  public SemServiceImpl(Project project) {
    this.myProject = project;
    this.myPsiModificationTracker = PsiModificationTracker.getInstance(project);
    SemContributor.EP_NAME.addChangeListener(() -> keysAndProducers = null, project);

    MessageBusConnection messageBusConnection = project.getMessageBus().connect(this);
    messageBusConnection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        keysAndProducers = null;
      }
    });
    messageBusConnection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void exitDumbMode() {
        keysAndProducers = null;
      }
    });
  }

  private State buildState() {
    var map = new MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>>();

    SemRegistrar registrar = new SemRegistrar() {
      @SuppressWarnings({"unchecked", "rawtypes"})
      @Override
      public <T extends SemElement> void registerSemProvider(
        SemKey<T> key,
        BiFunction<? super PsiElement, ? super ProcessingContext, ? extends Collection<T>> provider
      ) {
        map.putValue(key, (BiFunction)provider);
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
        return Unit.INSTANCE;
      }
      catch (Exception e) {
        Logger.getInstance(SemServiceImpl.class).error(e);
        return Unit.INSTANCE;
      }

      if (semContributor.isAvailable(myProject)) {
        semContributor.registerSemProviders(registrar, myProject);
      }
      return Unit.INSTANCE;
    });

    Map<SemKey<?>, Collection<SemKey<?>>> inheritors = new HashMap<>();
    for (SemKey<?> key : map.keySet()) {
      putInheritors(key, key, inheritors);
    }

    return new State(map, inheritors);
  }

  private static void putInheritors(SemKey<?> key, SemKey<?> eachParent, Map<SemKey<?>, Collection<SemKey<?>>> inheritors) {
    getInheritorsSet(inheritors, eachParent).add(eachParent); // always add itself as parent

    for (SemKey<?> aSuper : eachParent.getSupers()) {
      getInheritorsSet(inheritors, aSuper).add(key);
      putInheritors(key, aSuper, inheritors);
    }
  }

  private static @NotNull Collection<SemKey<?>> getInheritorsSet(Map<SemKey<?>, Collection<SemKey<?>>> inheritors, SemKey<?> aSuper) {
    return inheritors.computeIfAbsent(aSuper, k -> new LinkedHashSet<>());
  }

  @Override
  public @NotNull <T extends SemElement> List<T> getSemElements(@NotNull SemKey<T> key, @NotNull PsiElement psi) {
    var state = ensureInitialized();

    IntObjectMap<List<SemElement>> chunk = getUpToDate(getCacheHolder(psi));
    List<T> cached = findCached(state, key, chunk);
    return cached != null ? cached : collectSemElements(state, key, psi, chunk);
  }

  @Override
  public @NotNull <T extends SemElement> List<T> getSemElementsNoCache(SemKey<T> key, @NotNull PsiElement psi) {
    var state = ensureInitialized();

    SemData holder = getCacheHolderIfExist(psi);
    if (holder != null) {
      IntObjectMap<List<SemElement>> chunk = getUpToDate(holder);
      List<T> cached = findCached(state, key, chunk);
      if (cached != null) return cached;
    }

    return collectSemElements(state, key, psi, null);
  }

  private static @Nullable SemServiceImpl.SemData getCacheHolderIfExist(@NotNull PsiElement psi) {
    return psi.getUserData(SEM_CACHE_KEY);
  }

  private @NotNull SemServiceImpl.SemData getCacheHolder(@NotNull PsiElement psi) {
    SemData cacheHolder = psi.getUserData(SEM_CACHE_KEY);
    if (cacheHolder != null) {
      return cacheHolder;
    }

    if (psi instanceof UserDataHolderEx) {
      return ((UserDataHolderEx)psi).putUserDataIfAbsent(SEM_CACHE_KEY, new SemData(getModCount()));
    }

    SemData semData;
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (psi) {
      semData = psi.getUserData(SEM_CACHE_KEY);
      if (semData == null) {
        semData = new SemData(getModCount());
        psi.putUserData(SEM_CACHE_KEY, semData);
      }
    }
    return semData;
  }

  private long getModCount() {
    return myPsiModificationTracker.getModificationCount();
  }

  private @NotNull IntObjectMap<List<SemElement>> getUpToDate(@NotNull SemServiceImpl.SemData holder) {
    long currentModCount = getModCount();
    long cachedModCount = holder.modificationCount;
    if (currentModCount == cachedModCount) return holder.data;

    return holder.refresh(cachedModCount, currentModCount);
  }

  private static class SemData {
    private volatile long modificationCount;
    private final IntObjectMap<List<SemElement>> data = createConcurrentIntObjectMap(4, 0.75f, 2); // 8 elements initially

    private SemData(long count) {
      this.modificationCount = count;
    }

    public synchronized IntObjectMap<List<SemElement>> refresh(long expectedModCount, long currentModCount) {
      if (expectedModCount == modificationCount) {
        data.clear();
        this.modificationCount = currentModCount;
      }
      return data;
    }

    @Override
    public String toString() {
      return "SemData{" +
             "count=" + data.size() +
             '}';
    }
  }

  @SuppressWarnings("unchecked")
  private static @NotNull <T extends SemElement> List<T> collectSemElements(@NotNull State currentProducers,
                                                                            @NotNull SemKey<T> key,
                                                                            @NotNull PsiElement psi,
                                                                            @Nullable IntObjectMap<List<SemElement>> chunk) {
    RecursionGuard.StackStamp stamp = RecursionManager.markStack();

    List<T> result;
    Int2ObjectMap<List<SemElement>> map;

    ProcessingContext processingContext = new ProcessingContext();
    Collection<SemKey<?>> inheritors = currentProducers.inheritors.getOrDefault(key, emptyList());

    if (inheritors.size() == 1) {
      SemKey<?> singleKey = inheritors.iterator().next();
      List<SemElement> list = createSemElements(currentProducers, singleKey, psi, processingContext);
      map = Int2ObjectMaps.singleton(singleKey.getUniqueId(), list);
      result = (List<T>)list;
    }
    else {
      map = new Int2ObjectOpenHashMap<>(inheritors.size());
      result = new ArrayList<>();

      for (SemKey<?> each : inheritors) {
        List<SemElement> list = createSemElements(currentProducers, each, psi, processingContext);
        map.put(each.getUniqueId(), list);
        if (!list.isEmpty()) {
          result.addAll((List<T>)list);
        }
      }
    }

    if (chunk != null && stamp.mayCacheNow()) {
      Int2ObjectMaps.fastForEach(map, entry -> {
        putSemElements(chunk, entry.getIntKey(), entry.getValue());
      });
    }

    if (result.isEmpty()) return emptyList();
    if (result.size() == 1) return singletonList(result.get(0));

    return result.stream()
      .distinct()
      .toList();
  }

  private State ensureInitialized() {
    var current = keysAndProducers;
    if (current != null) {
      return current;
    }

    synchronized (lock) {
      current = keysAndProducers;
      if (current != null) {
        return current;
      }

      var newProducers = buildState();
      keysAndProducers = newProducers;
      return newProducers;
    }
  }

  private static @NotNull List<SemElement> createSemElements(
    State keysAndProducers,
    SemKey<?> key, PsiElement psi, ProcessingContext processingContext
  ) {
    List<SemElement> result = null;

    var functions = keysAndProducers.producers.get(key);
    if (!functions.isEmpty()) {
      for (var producer : functions) {
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
  private static @Nullable <T extends SemElement> List<T> findCached(@NotNull State state,
                                                                     @NotNull SemKey<T> key,
                                                                     @NotNull IntObjectMap<List<SemElement>> chunk) {
    List<T> singleList = null;
    LinkedHashSet<T> result = null;

    Collection<SemKey<?>> inheritors = state.inheritors.getOrDefault(key, emptyList());
    for (var inheritor : inheritors) {
      List<T> cached = (List<T>)getSemElements(chunk, inheritor);
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

  private static List<SemElement> getSemElements(IntObjectMap<List<SemElement>> semCache, SemKey<?> key) {
    return semCache.get(key.getUniqueId());
  }

  private static void putSemElements(IntObjectMap<List<SemElement>> semCache, int keyId, @NotNull List<SemElement> elements) {
    semCache.put(keyId, elements);
  }

  private static class State {
    final MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> producers;
    final Map<SemKey<?>, Collection<SemKey<?>>> inheritors;

    private State(MultiMap<SemKey<?>, BiFunction<PsiElement, ProcessingContext, Collection<? extends SemElement>>> producers,
                  Map<SemKey<?>, Collection<SemKey<?>>> inheritors) {
      this.producers = producers;
      this.inheritors = inheritors;
    }
  }
}
