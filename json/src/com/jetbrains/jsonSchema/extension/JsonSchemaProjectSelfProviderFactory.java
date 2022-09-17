// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import kotlin.NotImplementedError;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Irina.Chernushina on 2/24/2016.
 */
public class JsonSchemaProjectSelfProviderFactory implements JsonSchemaProviderFactory, DumbAware {
  public static final int TOTAL_PROVIDERS = 3;
  private static final String SCHEMA_JSON_FILE_NAME = "schema.json";
  private static final String SCHEMA06_JSON_FILE_NAME = "schema06.json";
  private static final String SCHEMA07_JSON_FILE_NAME = "schema07.json";

  @NotNull
  @Override
  public List<JsonSchemaFileProvider> getProviders(@NotNull final Project project) {
    return Arrays
      .asList(new MyJsonSchemaFileProvider(project, SCHEMA_JSON_FILE_NAME), new MyJsonSchemaFileProvider(project, SCHEMA06_JSON_FILE_NAME),
              new MyJsonSchemaFileProvider(project, SCHEMA07_JSON_FILE_NAME));
  }

  public static final class MyJsonSchemaFileProvider implements JsonSchemaFileProvider {
    @NotNull private final Project myProject;
    @NotNull private final @Nls String myFileName;

    public boolean isSchemaV4() {
      return SCHEMA_JSON_FILE_NAME.equals(myFileName);
    }
    public boolean isSchemaV6() {
      return SCHEMA06_JSON_FILE_NAME.equals(myFileName);
    }
    public boolean isSchemaV7() {
      return SCHEMA07_JSON_FILE_NAME.equals(myFileName);
    }

    private MyJsonSchemaFileProvider(@NotNull Project project, @NotNull @Nls String fileName) {
      myProject = project;
      myFileName = fileName;
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile file) {
      if (myProject.isDisposed()) return false;
      JsonSchemaService service = JsonSchemaService.Impl.get(myProject);
      if (!service.isApplicableToFile(file)) return false;
      JsonSchemaVersion schemaVersion = service.getSchemaVersion(file);
      if (schemaVersion == null) return false;
      return switch (schemaVersion) {
        case SCHEMA_4 -> isSchemaV4();
        case SCHEMA_6 -> isSchemaV6();
        case SCHEMA_7 -> isSchemaV7();
      };
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
      return JsonSchemaProviderFactory.getResourceFile(JsonSchemaProjectSelfProviderFactory.class, "/jsonSchema/" + myFileName);
    }

    @NotNull
    @Override
    public SchemaType getSchemaType() {
      return SchemaType.schema;
    }

    @Nullable
    @Override
    public String getRemoteSource() {
      return switch (myFileName) {
        case SCHEMA_JSON_FILE_NAME -> "http://json-schema.org/draft-04/schema";
        case SCHEMA06_JSON_FILE_NAME -> "http://json-schema.org/draft-06/schema";
        case SCHEMA07_JSON_FILE_NAME -> "http://json-schema.org/draft-07/schema";
        default -> null;
      };
    }

    @NotNull
    @Override
    public String getPresentableName() {
      return switch (myFileName) {
        case SCHEMA_JSON_FILE_NAME -> JsonBundle.message("schema.of.version", 4);
        case SCHEMA06_JSON_FILE_NAME -> JsonBundle.message("schema.of.version", 6);
        case SCHEMA07_JSON_FILE_NAME -> JsonBundle.message("schema.of.version", 7);
        default -> getName();
      };
    }
  }
}
