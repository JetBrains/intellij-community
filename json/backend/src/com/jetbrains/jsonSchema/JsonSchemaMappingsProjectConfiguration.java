// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

@State(name = "JsonSchemaMappingsProjectConfiguration", storages = @Storage("jsonSchemas.xml"))
public class JsonSchemaMappingsProjectConfiguration implements PersistentStateComponent<JsonSchemaMappingsProjectConfiguration.MyState> {
  private final @NotNull Project myProject;
  public volatile MyState myState = new MyState();

  public @Nullable UserDefinedJsonSchemaConfiguration findMappingBySchemaInfo(JsonSchemaInfo value) {
    for (UserDefinedJsonSchemaConfiguration configuration : myState.myState.values()) {
      if (areSimilar(value, configuration)) return configuration;
    }
    return null;
  }

  public boolean areSimilar(JsonSchemaInfo value, UserDefinedJsonSchemaConfiguration configuration) {
    return Objects.equals(normalizePath(value.getUrl(myProject)), normalizePath(configuration.getRelativePathToSchema()));
  }

  @Contract("null -> null; !null -> !null")
  public @Nullable String normalizePath(@Nullable String valueUrl) {
    if (valueUrl == null) return null;
    if (StringUtil.contains(valueUrl, "..")) {
      valueUrl = new File(valueUrl).getAbsolutePath();
    }
    return valueUrl.replace('\\', '/');
  }

  public @Nullable UserDefinedJsonSchemaConfiguration findMappingForFile(VirtualFile file) {
    VirtualFile projectBaseDir = myProject.getBaseDir();
    for (UserDefinedJsonSchemaConfiguration configuration : myState.myState.values()) {
      for (UserDefinedJsonSchemaConfiguration.Item pattern : configuration.patterns) {
        if (pattern.mappingKind != JsonMappingKind.File) continue;
        VirtualFile relativeFile = VfsUtil.findRelativeFile(projectBaseDir, pattern.getPathParts());
        if (Objects.equals(relativeFile, file) || file.getUrl().equals(UserDefinedJsonSchemaConfiguration.Item.neutralizePath(pattern.getPath()))) {
          return configuration;
        }
      }
    }
    return null;
  }

  public static JsonSchemaMappingsProjectConfiguration getInstance(final @NotNull Project project) {
    return project.getService(JsonSchemaMappingsProjectConfiguration.class);
  }

  public JsonSchemaMappingsProjectConfiguration(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @Nullable MyState getState() {
    return myState;
  }

  public void schemaFileMoved(final @NotNull Project project,
                              final @NotNull String oldRelativePath,
                              final @NotNull String newRelativePath) {
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

  public static final class MyState {
    @Tag("state")
    @XCollection
    public Map<String, UserDefinedJsonSchemaConfiguration> myState = new TreeMap<>();

    MyState() {
    }

    MyState(Map<String, UserDefinedJsonSchemaConfiguration> state) {
      myState = state;
    }
  }

  public boolean isIgnoredFile(VirtualFile virtualFile) {
    UserDefinedJsonSchemaConfiguration mappingForFile = findMappingForFile(virtualFile);
    return mappingForFile != null && mappingForFile.isIgnoredFile();
  }

  public void markAsIgnored(VirtualFile virtualFile) {
    UserDefinedJsonSchemaConfiguration existingMapping = findMappingForFile(virtualFile);
    if (existingMapping != null) {
      removeConfiguration(existingMapping);
    }
    addConfiguration(createIgnoreSchema(virtualFile.getUrl()));
  }

  public void unmarkAsIgnored(VirtualFile virtualFile) {
    if (isIgnoredFile(virtualFile)) {
      UserDefinedJsonSchemaConfiguration existingMapping = findMappingForFile(virtualFile);
      removeConfiguration(existingMapping);
    }
  }

  private static UserDefinedJsonSchemaConfiguration createIgnoreSchema(String ignoredFileUrl) {
    UserDefinedJsonSchemaConfiguration schemaConfiguration = new UserDefinedJsonSchemaConfiguration(
      JsonBundle.message("schema.widget.no.schema.label"),
      JsonSchemaVersion.SCHEMA_4,
      "",
      true,
      Collections.singletonList(new UserDefinedJsonSchemaConfiguration.Item(ignoredFileUrl, false, false))
    );
    schemaConfiguration.setIgnoredFile(true);
    return schemaConfiguration;
  }
}