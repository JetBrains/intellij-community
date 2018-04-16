// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.Project;
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
  public static final int TOTAL_PROVIDERS = 2;
  public static final String SCHEMA_JSON_FILE_NAME = "schema.json";
  public static final String SCHEMA06_JSON_FILE_NAME = "schema06.json";

  @NotNull
  @Override
  public List<JsonSchemaFileProvider> getProviders(@NotNull final Project project) {
    return ContainerUtil.list(new MyJsonSchemaFileProvider(project, SCHEMA_JSON_FILE_NAME),
                              new MyJsonSchemaFileProvider(project, SCHEMA06_JSON_FILE_NAME));
  }

  public static class MyJsonSchemaFileProvider implements JsonSchemaFileProvider {
    @NotNull private final Project myProject;
    @Nullable private final VirtualFile mySchemaFile;
    @NotNull private final String myFileName;

    public boolean isSchemaV4() {
      return SCHEMA_JSON_FILE_NAME.equals(myFileName);
    }
    public boolean isSchemaV6() {
      return SCHEMA06_JSON_FILE_NAME.equals(myFileName);
    }

    private MyJsonSchemaFileProvider(@NotNull final Project project, @NotNull String fileName) {
      myProject = project;
      myFileName = fileName;
      // schema file can not be static here, because in schema's user data we cache project-scope objects (i.e. which can refer to project)
      mySchemaFile = JsonSchemaProviderFactory.getResourceFile(JsonSchemaProjectSelfProviderFactory.class, "/jsonSchema/" + fileName);
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile file) {
      JsonSchemaVersion schemaVersion = JsonSchemaService.Impl.get(myProject).getSchemaVersion(file);
      if (schemaVersion == null) return false;
      switch (schemaVersion) {
        case SCHEMA_4:
          return isSchemaV4();
        case SCHEMA_6:
          return isSchemaV6();
      }

      throw new NotImplementedError("Unknown schema version: " + schemaVersion);
    }

    @Override
    public JsonSchemaVersion getSchemaVersion() {
      return isSchemaV4() ? JsonSchemaVersion.SCHEMA_4 : JsonSchemaVersion.SCHEMA_6;
    }

    @NotNull
    @Override
    public String getName() {
      return myFileName;
    }

    @Nullable
    @Override
    public VirtualFile getSchemaFile() {
      return mySchemaFile;
    }

    @NotNull
    @Override
    public SchemaType getSchemaType() {
      return SchemaType.schema;
    }
  }
}
