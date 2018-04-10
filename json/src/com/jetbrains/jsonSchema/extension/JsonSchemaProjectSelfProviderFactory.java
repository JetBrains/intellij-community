// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Irina.Chernushina on 2/24/2016.
 */
public class JsonSchemaProjectSelfProviderFactory implements JsonSchemaProviderFactory {
  public static final String SCHEMA_JSON_FILE_NAME = "schema.json";

  @NotNull
  @Override
  public List<JsonSchemaFileProvider> getProviders(@NotNull final Project project) {
    return Collections.singletonList(new MyJsonSchemaFileProvider(project));
  }

  private static class MyJsonSchemaFileProvider implements JsonSchemaFileProvider {
    @NotNull private final Project myProject;
    @Nullable private final VirtualFile mySchemaFile;

    private MyJsonSchemaFileProvider(@NotNull final Project project) {
      myProject = project;
      // schema file can not be static here, because in schema's user data we cache project-scope objects (i.e. which can refer to project)
      mySchemaFile = JsonSchemaProviderFactory.getResourceFile(JsonSchemaProjectSelfProviderFactory.class, "/jsonSchema/schema.json");
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile file) {
      return JsonSchemaService.Impl.get(myProject).isSchemaFile(file);
    }

    @NotNull
    @Override
    public String getName() {
      return SCHEMA_JSON_FILE_NAME;
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
