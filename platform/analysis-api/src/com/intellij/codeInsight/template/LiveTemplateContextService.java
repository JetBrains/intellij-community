// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service(Service.Level.APP)
@ApiStatus.Internal
public final class LiveTemplateContextService implements Disposable {
  private volatile LiveTemplateContextsState myState;

  public LiveTemplateContextService() {
    myState = loadLiveTemplateContextsNoLock();

    LiveTemplateContextBean.EP_NAME.addChangeListener(this::reloadLiveTemplateContexts, this);
    LiveTemplateContextProvider.EP_NAME.addChangeListener(this::reloadLiveTemplateContexts, this);
    LiveTemplateInternalContextBean.EP_NAME.addChangeListener(this::reloadLiveTemplateContexts, this);
  }

  public static LiveTemplateContextService getInstance() {
    return ApplicationManager.getApplication().getService(LiveTemplateContextService.class);
  }
                        
  public @NotNull @Unmodifiable Collection<@NotNull LiveTemplateContext> getLiveTemplateContexts() {
    return myState.myLiveTemplateIds.values();
  }

  public @Nullable LiveTemplateContext getLiveTemplateContext(@Nullable String id) {
    if (id == null) return null;

    id = getInternalIds().getOrDefault(id, id);
    return myState.myLiveTemplateIds.get(id);
  }

  public @NotNull Map<@NotNull String, @NotNull String> getInternalIds() {
    return myState.myInternalIds;
  }

  public @Nullable LiveTemplateContext getLiveTemplateContext(@NotNull Class<?> clazz) {
    LiveTemplateContextsState currentState = myState;

    LiveTemplateContext existingBean = currentState.myLiveTemplateClasses.get(clazz);
    if (existingBean != null) {
      return existingBean;
    }

    for (LiveTemplateContext bean : currentState.myLiveTemplateIds.values()) {
      TemplateContextType instance = bean.getTemplateContextType();
      if (clazz.isInstance(instance)) {
        currentState.myLiveTemplateClasses.put(clazz, bean);
        return bean;
      }
    }

    return null;
  }

  public @NotNull TemplateContextType getTemplateContextType(@NotNull String id) {
    LiveTemplateContext context = getLiveTemplateContext(id);
    if (context == null) throw new LiveTemplateContextNotFoundException("Unable to find LiveTemplateContext with contextId " + id);

    return context.getTemplateContextType();
  }

  public @NotNull TemplateContextType getTemplateContextType(@NotNull Class<?> clazz) {
    LiveTemplateContext context = getLiveTemplateContext(clazz);
    if (context == null) throw new LiveTemplateContextNotFoundException("Unable to find LiveTemplateContext with class " + clazz);

    return context.getTemplateContextType();
  }

  private void reloadLiveTemplateContexts() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    // reset previously calculated base contexts
    for (LiveTemplateContext liveTemplateContext : myState.myLiveTemplateIds.values()) {
      liveTemplateContext.getTemplateContextType().clearCachedBaseContextType();
    }

    myState = loadLiveTemplateContextsNoLock();
  }

  private static LiveTemplateContextsState loadLiveTemplateContextsNoLock() {
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

    var internalIds = LiveTemplateInternalContextBean.EP_NAME.getExtensionList().stream()
      .collect(Collectors.toMap(it -> it.internalContextId, it -> it.contextId));

    return new LiveTemplateContextsState(allIdsMap, internalIds);
  }

  public @NotNull LiveTemplateContextsSnapshot getSnapshot() {
    return new LiveTemplateContextsSnapshot(myState.myLiveTemplateIds); // myLiveTemplateIds is immutable
  }

  @Override
  public void dispose() {
  }
}

final class LiveTemplateContextsState {
  public final Map<String, LiveTemplateContext> myLiveTemplateIds;
  public final Map<String, String> myInternalIds;

  // used as lookup cache
  public final Map<Class<?>, LiveTemplateContext> myLiveTemplateClasses = new ConcurrentHashMap<>();

  LiveTemplateContextsState(Map<String, LiveTemplateContext> liveTemplateIds, Map<String, String> internalIds) {
    myLiveTemplateIds = liveTemplateIds;
    myInternalIds = internalIds;
  }
}

final class LiveTemplateContextNotFoundException extends IllegalStateException {
  LiveTemplateContextNotFoundException(String s) {
    super(s);
  }
}