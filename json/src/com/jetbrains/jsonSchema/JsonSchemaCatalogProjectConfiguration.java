// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Tag;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "JsonSchemaCatalogProjectConfiguration", storages = @Storage("jsonCatalog.xml"))
public class JsonSchemaCatalogProjectConfiguration implements PersistentStateComponent<JsonSchemaCatalogProjectConfiguration.MyState> {
  @NotNull private final Project myProject;
  public volatile MyState myState = new MyState();

  public static JsonSchemaCatalogProjectConfiguration getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, JsonSchemaCatalogProjectConfiguration.class);
  }

  public JsonSchemaCatalogProjectConfiguration(@NotNull Project project) {
    myProject = project;
  }

  public void setState(boolean isEnabled) {
    myState = new MyState(isEnabled);
    updateComponent(isEnabled);
  }

  private void updateComponent(boolean isEnabled) {
    ((JsonSchemaServiceImpl)JsonSchemaService.Impl.get(myProject)).getCatalogManager().setEnabled(isEnabled);
  }

  @Nullable
  @Override
  public MyState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    myState = state;
    updateComponent(state.myIsEnabled);
  }

  static class MyState {
    @Tag("enabled")
    public boolean myIsEnabled = true;

    public MyState() {
    }

    public MyState(boolean isEnabled) { myIsEnabled = isEnabled; }
  }
}
