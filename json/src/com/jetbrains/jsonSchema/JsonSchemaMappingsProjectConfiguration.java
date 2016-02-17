package com.jetbrains.jsonSchema;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
}
