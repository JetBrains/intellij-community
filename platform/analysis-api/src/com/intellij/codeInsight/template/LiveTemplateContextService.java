// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service(Service.Level.APP)
@ApiStatus.Internal
public final class LiveTemplateContextService implements Disposable {
  private final ReadWriteLock myRwLock = new ReentrantReadWriteLock();

  private Map<String, LiveTemplateContext> myLiveTemplateIds = Map.of();
  private final Map<Class<?>, LiveTemplateContext> myLiveTemplateClasses = new ConcurrentHashMap<>();
  private Map<String, String> myInternalIds = new HashMap<>();

  public LiveTemplateContextService() {
    loadLiveTemplateContexts();

    LiveTemplateContextBean.EP_NAME.addChangeListener(this::loadLiveTemplateContexts, this);
    LiveTemplateContextProvider.EP_NAME.addChangeListener(this::loadLiveTemplateContexts, this);
  }

  public static LiveTemplateContextService getInstance() {
    return ApplicationManager.getApplication().getService(LiveTemplateContextService.class);
  }

  public @NotNull Collection<@NotNull LiveTemplateContext> getLiveTemplateContexts() {
    myRwLock.readLock().lock();
    try {
      return myLiveTemplateIds.values();
    }
    finally {
      myRwLock.readLock().unlock();
    }
  }

  public @Nullable LiveTemplateContext getLiveTemplateContext(@Nullable String id) {
    if (id == null) return null;

    myRwLock.readLock().lock();
    try {
      return myLiveTemplateIds.get(id);
    }
    finally {
      myRwLock.readLock().unlock();
    }
  }

  public @NotNull Map<@NotNull String, @NotNull String> getInternalIds() {
    myRwLock.readLock().lock();
    try {
      return myInternalIds;
    }
    finally {
      myRwLock.readLock().unlock();
    }
  }

  public @Nullable LiveTemplateContext getLiveTemplateContext(@NotNull Class<?> clazz) {
    myRwLock.readLock().lock();
    try {
      LiveTemplateContext existingBean = myLiveTemplateClasses.get(clazz);
      if (existingBean != null) {
        return existingBean;
      }

      for (LiveTemplateContext bean : myLiveTemplateIds.values()) {
        TemplateContextType instance = bean.getTemplateContextType();
        if (clazz.isInstance(instance)) {
          myLiveTemplateClasses.put(clazz, bean);
          return bean;
        }
      }

      return null;
    }
    finally {
      myRwLock.readLock().unlock();
    }
  }

  public @NotNull TemplateContextType getTemplateContextType(@NotNull String id) {
    LiveTemplateContext context = getLiveTemplateContext(id);
    if (context == null) throw new IllegalStateException("Unable to find LiveTemplateContext with contextId " + id);

    return context.getTemplateContextType();
  }

  public @NotNull TemplateContextType getTemplateContextType(@NotNull Class<?> clazz) {
    LiveTemplateContext context = getLiveTemplateContext(clazz);
    if (context == null) throw new IllegalStateException("Unable to find LiveTemplateContext with class " + clazz);

    return context.getTemplateContextType();
  }

  private void loadLiveTemplateContexts() {
    myRwLock.writeLock().lock();
    try {
      // reset previously calculated base contexts
      for (LiveTemplateContext liveTemplateContext : myLiveTemplateIds.values()) {
        liveTemplateContext.getTemplateContextType().clearCachedBaseContextType();
      }

      List<LiveTemplateContextBean> allBeans = LiveTemplateContextBean.EP_NAME.getExtensionList();
      Map<String, LiveTemplateContext> allIdsMap = new LinkedHashMap<>();
      for (LiveTemplateContextBean bean : allBeans) {
        allIdsMap.put(bean.getContextId(), bean);
      }

      for (LiveTemplateContextProvider provider : LiveTemplateContextProvider.EP_NAME.getExtensionList()) {
        for (LiveTemplateContext contextType : provider.createContexts()) {
          allIdsMap.put(contextType.getContextId(), contextType);
        }
      }

      this.myLiveTemplateIds = allIdsMap;
      this.myLiveTemplateClasses.clear();

      this.myInternalIds = allIdsMap.values().stream()
        .map(LiveTemplateContext::getContextId)
        .distinct()
        .collect(Collectors.toMap(Function.identity(), Function.identity()));
    }
    finally {
      myRwLock.writeLock().unlock();
    }
  }

  public @NotNull LiveTemplateContextsSnapshot getSnapshot() {
    myRwLock.readLock().lock();
    try {
      return new LiveTemplateContextsSnapshot(myLiveTemplateIds); // myLiveTemplateIds is immutable
    }
    finally {
      myRwLock.readLock().unlock();
    }
  }

  @Override
  public void dispose() {
  }
}
