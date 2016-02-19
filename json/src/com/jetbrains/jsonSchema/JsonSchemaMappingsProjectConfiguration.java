package com.jetbrains.jsonSchema;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Irina.Chernushina on 2/2/2016.
 */
@State(
  name = "JsonSchemaMappingsProjectConfiguration",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/jsonSchemas.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class JsonSchemaMappingsProjectConfiguration extends JsonSchemaMappingsConfigurationBase {
  private Project myProject;
  @Transient
  private final Map<VirtualFile, SchemaInfo> mySchemaFiles = new HashMap<>();

  public static JsonSchemaMappingsProjectConfiguration getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, JsonSchemaMappingsProjectConfiguration.class);
  }

  public JsonSchemaMappingsProjectConfiguration(Project project) {
    myProject = project;
  }

  public JsonSchemaMappingsProjectConfiguration() {
  }

  @Override
  public File convertToAbsoluteFile(@NotNull String path) {
    return myProject.getBasePath() == null ? new File(path) : new File(myProject.getBasePath(), path);
  }

  public boolean isRegisteredSchemaFile(@NotNull VirtualFile file) {
    return mySchemaFiles.containsKey(file);
  }

  @Override
  public void setState(@NotNull Map<String, SchemaInfo> state) {
    super.setState(state);
    recalculateSchemaFiles();
  }

  @Override
  public void addSchema(@NotNull SchemaInfo info) {
    super.addSchema(info);
    recalculateSchemaFiles();
  }

  @Override
  public void removeSchema(@NotNull SchemaInfo info) {
    super.removeSchema(info);
    recalculateSchemaFiles();
  }

  @Override
  public void loadState(JsonSchemaMappingsConfigurationBase state) {
    super.loadState(state);
    recalculateSchemaFiles();
  }

  private void recalculateSchemaFiles() {
    mySchemaFiles.clear();
    if (myProject == null || myProject.getBaseDir() == null) return;

    for (JsonSchemaMappingsConfigurationBase.SchemaInfo info : myState.values()) {
      final VirtualFile schemaFile = info.getSchemaFile(myProject);
      if (schemaFile != null) mySchemaFiles.put(schemaFile, info);
    }
  }

  @Nullable
  public SchemaInfo getSchemaBySchemaFile(@NotNull final VirtualFile file) {
    return mySchemaFiles.get(file);
  }
}
