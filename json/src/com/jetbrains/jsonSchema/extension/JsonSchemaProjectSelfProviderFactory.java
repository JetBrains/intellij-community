/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
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
    public static final Pair<SchemaType, Object> KEY = Pair.create(SchemaType.schema, SchemaType.schema);
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
