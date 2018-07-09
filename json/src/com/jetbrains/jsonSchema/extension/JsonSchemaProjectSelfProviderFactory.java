// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import kotlin.NotImplementedError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Irina.Chernushina on 2/24/2016.
 */
public class JsonSchemaProjectSelfProviderFactory implements JsonSchemaProviderFactory {
  public static final int TOTAL_PROVIDERS = 3;
  private static final String SCHEMA_JSON_FILE_NAME = "schema.json";
  private static final String SCHEMA06_JSON_FILE_NAME = "schema06.json";
  private static final String SCHEMA07_JSON_FILE_NAME = "schema07.json";

  @NotNull
  @Override
  public List<JsonSchemaFileProvider> getProviders(@NotNull final Project project) {
    return ContainerUtil.list(new MyJsonSchemaFileProvider(project, SCHEMA_JSON_FILE_NAME),
                              new MyJsonSchemaFileProvider(project, SCHEMA06_JSON_FILE_NAME),
                              new MyJsonSchemaFileProvider(project, SCHEMA07_JSON_FILE_NAME));
  }

  public static class MyJsonSchemaFileProvider implements JsonSchemaFileProvider {
    @NotNull private final Project myProject;
    @NotNull private final NullableLazyValue<VirtualFile> mySchemaFile;
    @NotNull private final String myFileName;

    public boolean isSchemaV4() {
      return SCHEMA_JSON_FILE_NAME.equals(myFileName);
    }
    public boolean isSchemaV6() {
      return SCHEMA06_JSON_FILE_NAME.equals(myFileName);
    }
    public boolean isSchemaV7() {
      return SCHEMA07_JSON_FILE_NAME.equals(myFileName);
    }

    private MyJsonSchemaFileProvider(@NotNull final Project project, @NotNull String fileName) {
      myProject = project;
      myFileName = fileName;
      // schema file can not be static here, because in schema's user data we cache project-scope objects (i.e. which can refer to project)
      mySchemaFile = NullableLazyValue.createValue(() -> JsonSchemaProviderFactory.getResourceFile(JsonSchemaProjectSelfProviderFactory.class, "/jsonSchema/" + fileName));
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile file) {
      JsonSchemaService service = JsonSchemaService.Impl.get(myProject);
      if (!service.isApplicableToFile(file)) return false;
      JsonSchemaVersion schemaVersion = service.getSchemaVersion(file);
      if (schemaVersion == null) return false;
      switch (schemaVersion) {
        case SCHEMA_4:
          return isSchemaV4();
        case SCHEMA_6:
          return isSchemaV6();
        case SCHEMA_7:
          return isSchemaV7();
      }

      throw new NotImplementedError("Unknown schema version: " + schemaVersion);
    }

    @Override
    public JsonSchemaVersion getSchemaVersion() {
      return isSchemaV4() ? JsonSchemaVersion.SCHEMA_4 : isSchemaV7() ? JsonSchemaVersion.SCHEMA_7 : JsonSchemaVersion.SCHEMA_6;
    }

    @NotNull
    @Override
    public String getName() {
      return myFileName;
    }

    @Nullable
    @Override
    public VirtualFile getSchemaFile() {
      return mySchemaFile.getValue();
    }

    @NotNull
    @Override
    public SchemaType getSchemaType() {
      return SchemaType.schema;
    }

    @Nullable
    @Override
    public String getRemoteSource() {
      switch (myFileName) {
        case SCHEMA_JSON_FILE_NAME:
          return "http://json-schema.org/draft-04/schema";
        case SCHEMA06_JSON_FILE_NAME:
          return "http://json-schema.org/draft-06/schema";
        case SCHEMA07_JSON_FILE_NAME:
          return "http://json-schema.org/draft-07/schema";
      }
      return null;
    }

    @NotNull
    @Override
    public String getPresentableName() {
      switch (myFileName) {
        case SCHEMA_JSON_FILE_NAME:
          return "JSON schema v4";
        case SCHEMA06_JSON_FILE_NAME:
          return "JSON schema v6";
        case SCHEMA07_JSON_FILE_NAME:
          return "JSON schema v7";
      }
      return getName();
    }
  }
}
