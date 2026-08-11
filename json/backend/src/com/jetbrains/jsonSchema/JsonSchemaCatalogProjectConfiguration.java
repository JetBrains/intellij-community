// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Service(Service.Level.PROJECT)
@State(name = "JsonSchemaCatalogProjectConfiguration", storages = @Storage("jsonCatalog.xml"))
public final class JsonSchemaCatalogProjectConfiguration implements PersistentStateComponent<JsonSchemaCatalogProjectConfiguration.MyState> {
  public volatile MyState myState = new MyState();
  private final List<Runnable> myChangeHandlers = ContainerUtil.createConcurrentList();

  public boolean isCatalogEnabled() {
    MyState state = getState();
    return state != null && state.myIsCatalogEnabled;
  }

  public boolean isPreferRemoteSchemas() {
    MyState state = getState();
    return state != null && state.myIsPreferRemoteSchemas;
  }

  public boolean isImplicitSchemasEnabled() {
    MyState state = getState();
    return state == null || state.myIsImplicitSchemasEnabled;
  }

  public void addChangeHandler(Runnable runnable, @NotNull Disposable parentDisposable) {
    myChangeHandlers.add(runnable);
    Disposer.register(parentDisposable, () -> myChangeHandlers.remove(runnable));
  }

  public static JsonSchemaCatalogProjectConfiguration getInstance(final @NotNull Project project) {
    return project.getService(JsonSchemaCatalogProjectConfiguration.class);
  }

  public JsonSchemaCatalogProjectConfiguration() {
  }

  public void setState(boolean isEnabled, boolean isRemoteActivityEnabled, boolean isPreferRemoteSchemas) {
    MyState state = getState();
    setState(isEnabled,
             isRemoteActivityEnabled,
             isPreferRemoteSchemas,
             state == null || state.myIsImplicitSchemasEnabled);
  }

  public void setState(boolean isEnabled,
                       boolean isRemoteActivityEnabled,
                       boolean isPreferRemoteSchemas,
                       boolean isImplicitSchemasEnabled) {
    myState = new MyState(isEnabled, isRemoteActivityEnabled, isPreferRemoteSchemas, isImplicitSchemasEnabled);
    for (Runnable handler : myChangeHandlers) {
      handler.run();
    }
  }

  public void setCatalogEnabled(boolean isEnabled) {
    MyState state = getState();
    setState(isEnabled,
             state == null || state.myIsRemoteActivityEnabled,
             state != null && state.myIsPreferRemoteSchemas,
             state == null || state.myIsImplicitSchemasEnabled);
  }

  @Override
  public @Nullable MyState getState() {
    return myState;
  }

  public boolean isRemoteActivityEnabled() {
    MyState state = getState();
    return state != null && state.myIsRemoteActivityEnabled;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    myState = state;
    for (Runnable handler : myChangeHandlers) {
      handler.run();
    }
  }

  static final class MyState {
    @Tag("enabled")
    public boolean myIsCatalogEnabled = true;

    @Tag("remoteActivityEnabled")
    public boolean myIsRemoteActivityEnabled = true;

    @Tag("preferRemoteSchemas")
    public boolean myIsPreferRemoteSchemas = false;

    @Tag("implicitSchemasEnabled")
    public boolean myIsImplicitSchemasEnabled = true;

    MyState() {
    }

    MyState(boolean isCatalogEnabled,
            boolean isRemoteActivityEnabled,
            boolean isPreferRemoteSchemas,
            boolean isImplicitSchemasEnabled) {
      myIsCatalogEnabled = isCatalogEnabled;
      myIsRemoteActivityEnabled = isRemoteActivityEnabled;
      myIsPreferRemoteSchemas = isPreferRemoteSchemas;
      myIsImplicitSchemasEnabled = isImplicitSchemasEnabled;
    }
  }
}
