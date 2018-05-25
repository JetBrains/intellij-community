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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "JsonSchemaMappingsProjectConfiguration", storages = @Storage("jsonSchemas.xml"))
public class JsonSchemaMappingsProjectConfiguration implements PersistentStateComponent<JsonSchemaMappingsProjectConfiguration.MyState> {
  @NotNull private final Project myProject;
  public volatile MyState myState = new MyState();

  @Nullable
  public UserDefinedJsonSchemaConfiguration findMappingBySchemaInfo(JsonSchemaInfo value) {
    for (UserDefinedJsonSchemaConfiguration configuration : myState.myState.values()) {
      if (Objects.equals(value.getUrl(myProject), configuration.getRelativePathToSchema())) return configuration;
    }
    return null;
  }

  @Nullable
  public UserDefinedJsonSchemaConfiguration findMappingForFile(VirtualFile file) {
    VirtualFile projectBaseDir = myProject.getBaseDir();
    for (UserDefinedJsonSchemaConfiguration configuration : myState.myState.values()) {
      for (UserDefinedJsonSchemaConfiguration.Item pattern : configuration.patterns) {
        if (pattern.pattern || pattern.directory) continue;
        String path = pattern.path.replace('\\', '/');
        VirtualFile relativeFile = VfsUtil.findRelativeFile(projectBaseDir, StringUtil.split(path, "/").toArray(ArrayUtil.EMPTY_STRING_ARRAY));
        if (Objects.equals(relativeFile, file) || file.getUrl().equals(path)) {
          return configuration;
        }
      }
    }
    return null;
  }

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

  public void removeConfiguration(UserDefinedJsonSchemaConfiguration configuration) {
    for (Map.Entry<String, UserDefinedJsonSchemaConfiguration> entry : myState.myState.entrySet()) {
      if (entry.getValue() == configuration) {
        myState.myState.remove(entry.getKey());
        return;
      }
    }
  }

  public void addConfiguration(UserDefinedJsonSchemaConfiguration configuration) {
    String name = configuration.getName();
    while (myState.myState.containsKey(name)) {
      name += "1";
    }
    myState.myState.put(name, configuration);
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