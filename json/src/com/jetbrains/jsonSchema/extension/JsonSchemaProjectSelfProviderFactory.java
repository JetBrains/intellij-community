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
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Irina.Chernushina on 2/24/2016.
 */
public class JsonSchemaProjectSelfProviderFactory implements JsonSchemaProviderFactory {
  public static final String SCHEMA_JSON_FILE_NAME = "schema.json";
  private final List<JsonSchemaFileProvider> myProviders;

  public JsonSchemaProjectSelfProviderFactory() {
    myProviders = Collections.singletonList(new MyJsonSchemaFileProvider());
  }

  @Override
  public List<JsonSchemaFileProvider> getProviders(@Nullable Project project) {
    return myProviders;
  }

  private static class MyJsonSchemaFileProvider implements JsonSchemaFileProvider {
    public static final Pair<SchemaType, Object> KEY = Pair.create(SchemaType.schema, SchemaType.schema);
    private final VirtualFile mySchemaFile = JsonSchemaProviderFactory.getResourceFile(JsonSchemaProjectSelfProviderFactory.class, "/jsonSchema/schema.json");

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull VirtualFile file) {
      if (!JsonSchemaFileType.INSTANCE.equals(file.getFileType())) return false;
      return JsonSchemaMappingsProjectConfiguration.getInstance(project).isRegisteredSchemaFile(file);
    }

    @NotNull
    @Override
    public String getName() {
      return SCHEMA_JSON_FILE_NAME;
    }

    @Override
    public VirtualFile getSchemaFile() {
      return mySchemaFile;
    }

    @Override
    public SchemaType getSchemaType() {
      return SchemaType.schema;
    }

    @Override
    public int getOrder() {
      return Orders.CORE;
    }
  }
}
