/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.jsonSchema;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@State(name = "JsonSchemaMappingsProjectConfiguration", storages = @Storage("jsonSchemas.xml"))
public class JsonSchemaMappingsProjectConfiguration implements PersistentStateComponent<JsonSchemaMappingsProjectConfiguration.MyState> {
  @NotNull private final Project myProject;
  public volatile MyState myState = new MyState();

  public static JsonSchemaMappingsProjectConfiguration getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, JsonSchemaMappingsProjectConfiguration.class);
  }

  public JsonSchemaMappingsProjectConfiguration(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public MyState getState() {
    return myState;
  }

  public void schemaFileMoved(@NotNull final Project project,
                              @NotNull final String oldRelativePath,
                              @NotNull final String newRelativePath) {
      final Optional<UserDefinedJsonSchemaConfiguration> old = myState.myState.values().stream()
        .filter(schema -> FileUtil.pathsEqual(schema.getRelativePathToSchema(), oldRelativePath))
        .findFirst();
      old.ifPresent(configuration -> {
        configuration.setRelativePathToSchema(newRelativePath);
        JsonSchemaService.Impl.get(project).reset();
      });
  }

  public Map<String, UserDefinedJsonSchemaConfiguration> getStateMap() {
    return Collections.unmodifiableMap(myState.myState);
  }

  @Override
  public void loadState(@NotNull MyState state) {
    myState = state;
    JsonSchemaService.Impl.get(myProject).reset();
  }

  public void setState(@NotNull Map<String, UserDefinedJsonSchemaConfiguration> state) {
    myState = new MyState(state);
  }

  static class MyState {
    @Tag("state")
    @XCollection
    public Map<String, UserDefinedJsonSchemaConfiguration> myState = new TreeMap<>();

    public MyState() {
    }

    public MyState(Map<String, UserDefinedJsonSchemaConfiguration> state) {
      myState = state;
    }
  }
}