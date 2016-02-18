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

import com.intellij.json.JsonFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ResourceUtil;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;

/**
 * @author Irina.Chernushina on 2/16/2016.
 */
public class JsonSchemaSelfProviderFactory implements JsonSchemaProviderFactory {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.jsonSchema.extension.JsonSchemaSelfProviderFactory");

  @Override
  public JsonSchemaFileProvider[] getProviders(@Nullable Project project) {
    return new JsonSchemaFileProvider[] {
      new JsonSchemaFileProvider() {
        @Override
        public boolean isAvailable(@NotNull VirtualFile file) {
          if (project == null || !JsonFileType.INSTANCE.equals(file.getFileType())) return false;
          return JsonSchemaMappingsProjectConfiguration.getInstance(project).isRegisteredSchemaFile(file);
        }

        @Nullable
        @Override
        public Reader getSchemaReader() {
          final String content = getContent();
          return content == null ? null : new StringReader(content);
        }

        @NotNull
        @Override
        public String getName() {
          return "schema.json";
        }

        @Nullable
        private String getContent() {
          ClassLoader loader = JsonSchemaSelfProviderFactory.class.getClassLoader();
          try {
            URL resource = loader.getResource("jsonSchema/schema.json");
            assert resource != null;

            return ResourceUtil.loadText(resource);
          }
          catch (IOException e) {
            LOG.error(e.getMessage(), e);
          }

          return null;
        }
      }
    };
  }
}
