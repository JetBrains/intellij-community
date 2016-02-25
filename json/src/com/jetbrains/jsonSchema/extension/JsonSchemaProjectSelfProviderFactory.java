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
import com.intellij.openapi.components.ServiceManager;
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
 * @author Irina.Chernushina on 2/24/2016.
 */
public class JsonSchemaProjectSelfProviderFactory {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.jsonSchema.extension.JsonSchemaProjectSelfProviderFactory");
  private final JsonSchemaFileProvider[] myProviders;

  public static JsonSchemaProjectSelfProviderFactory getInstance(final Project project) {
    return ServiceManager.getService(project, JsonSchemaProjectSelfProviderFactory.class);
  }

  public JsonSchemaProjectSelfProviderFactory(final Project project) {
    myProviders = new JsonSchemaFileProvider[]{
      new MyJsonSchemaFileProvider(project)
    };
  }

  public JsonSchemaFileProvider[] getProviders() {
    return myProviders;
  }

  private static class MyJsonSchemaFileProvider implements JsonSchemaFileProvider {
    private final Project myProject;

    public MyJsonSchemaFileProvider(Project project) {
      myProject = project;
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile file) {
      if (myProject == null || !JsonFileType.INSTANCE.equals(file.getFileType())) return false;
      return JsonSchemaMappingsProjectConfiguration.getInstance(myProject).isRegisteredSchemaFile(file);
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
    private static String getContent() {
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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyJsonSchemaFileProvider provider = (MyJsonSchemaFileProvider)o;

      if (myProject != null ? !myProject.equals(provider.myProject) : provider.myProject != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myProject != null ? myProject.hashCode() : 0;
    }
  }
}
