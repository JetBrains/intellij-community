package com.jetbrains.jsonSchema;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * @author Irina.Chernushina on 2/2/2016.
 */
@State(
  name = "JsonSchemaMappingsProjectConfiguration",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/jsonSchemas.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class JsonSchemaMappingsProjectConfiguration implements PersistentStateComponent<JsonSchemaMappingsProjectConfiguration.MyState> {
  public volatile MyState myState = new MyState();

  public static JsonSchemaMappingsProjectConfiguration getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, JsonSchemaMappingsProjectConfiguration.class);
  }

  public JsonSchemaMappingsProjectConfiguration() {
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
  public void loadState(MyState state) {
    myState = state;
  }

  public void setState(@NotNull Map<String, UserDefinedJsonSchemaConfiguration> state) {
    myState = new MyState(state);
  }

  static class MyState {
    @Tag("state") @AbstractCollection(surroundWithTag = false)
    public Map<String, UserDefinedJsonSchemaConfiguration> myState = new TreeMap<>();

    public MyState() {
    }

    public MyState(Map<String, UserDefinedJsonSchemaConfiguration> state) {
      myState = state;
    }
  }
}