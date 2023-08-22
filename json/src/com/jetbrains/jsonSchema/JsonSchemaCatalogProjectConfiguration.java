// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(name = "JsonSchemaCatalogProjectConfiguration", storages = @Storage("jsonCatalog.xml"))
public class JsonSchemaCatalogProjectConfiguration implements PersistentStateComponent<JsonSchemaCatalogProjectConfiguration.MyState> {
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

  public void addChangeHandler(Runnable runnable) {
    myChangeHandlers.add(runnable);
  }

  public static JsonSchemaCatalogProjectConfiguration getInstance(@NotNull final Project project) {
    return project.getService(JsonSchemaCatalogProjectConfiguration.class);
  }

  public JsonSchemaCatalogProjectConfiguration() {
  }

  public void setState(boolean isEnabled, boolean isRemoteActivityEnabled, boolean isPreferRemoteSchemas) {
    myState = new MyState(isEnabled, isRemoteActivityEnabled, isPreferRemoteSchemas);
    for (Runnable handler : myChangeHandlers) {
      handler.run();
    }
  }

  @Nullable
  @Override
  public MyState getState() {
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

  static class MyState {
    @Tag("enabled")
    public boolean myIsCatalogEnabled = true;

    @Tag("remoteActivityEnabled")
    public boolean myIsRemoteActivityEnabled = true;

    @Tag("preferRemoteSchemas")
    public boolean myIsPreferRemoteSchemas = false;

    MyState() {
    }

    MyState(boolean isCatalogEnabled, boolean isRemoteActivityEnabled, boolean isPreferRemoteSchemas) {
      myIsCatalogEnabled = isCatalogEnabled;
      myIsRemoteActivityEnabled = isRemoteActivityEnabled;
      myIsPreferRemoteSchemas = isPreferRemoteSchemas;
    }
  }
}
