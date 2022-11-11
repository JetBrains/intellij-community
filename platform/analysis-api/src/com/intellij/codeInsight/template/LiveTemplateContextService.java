// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service(Service.Level.APP)
public final class LiveTemplateContextService implements Disposable {
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  private Map<String, LiveTemplateContext> liveTemplateIds = Map.of();
  private final Map<Class<?>, LiveTemplateContext> liveTemplateClasses = new ConcurrentHashMap<>();

  private Map<String, String> internalIds = new HashMap<>();

  public LiveTemplateContextService() {
    loadLiveTemplateContexts();

    LiveTemplateContextBean.EP_NAME.addChangeListener(this::loadLiveTemplateContexts, this);
    LiveTemplateContextProvider.EP_NAME.addChangeListener(this::loadLiveTemplateContexts, this);
  }

  public static LiveTemplateContextService getInstance() {
    return ApplicationManager.getApplication().getService(LiveTemplateContextService.class);
  }

  public @NotNull Collection<LiveTemplateContext> getLiveTemplateContexts() {
    lock.readLock().lock();
    try {
      return liveTemplateIds.values();
    }
    finally {
      lock.readLock().unlock();
    }
  }

  public @Nullable LiveTemplateContext getLiveTemplateContext(@NotNull String id) {
    lock.readLock().lock();
    try {
      return liveTemplateIds.get(id);
    }
    finally {
      lock.readLock().unlock();
    }
  }

  public @NotNull Map<String, String> getInternalIds() {
    lock.readLock().lock();
    try {
      return internalIds;
    }
    finally {
      lock.readLock().unlock();
    }
  }

  public @Nullable LiveTemplateContext getLiveTemplateContext(@NotNull Class<?> clazz) {
    lock.readLock().lock();
    try {
      LiveTemplateContext existingBean = liveTemplateClasses.get(clazz);
      if (existingBean != null) {
        return existingBean;
      }

      for (LiveTemplateContext bean : liveTemplateIds.values()) {
        TemplateContextType instance = bean.getTemplateContextType();
        if (clazz.isInstance(instance)) {
          liveTemplateClasses.put(clazz, bean);
          return bean;
        }
      }

      return null;
    }
    finally {
      lock.readLock().unlock();
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
    lock.writeLock().lock();
    try {
      // reset previously calculated base contexts
      for (LiveTemplateContext liveTemplateContext : liveTemplateIds.values()) {
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

      this.liveTemplateIds = allIdsMap;
      this.liveTemplateClasses.clear();

      this.internalIds = allIdsMap.values().stream()
        .map(LiveTemplateContext::getContextId)
        .distinct()
        .collect(Collectors.toMap(Function.identity(), Function.identity()));
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void dispose() {
  }
}
