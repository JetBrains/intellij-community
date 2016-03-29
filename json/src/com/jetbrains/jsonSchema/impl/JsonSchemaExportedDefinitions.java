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
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.jetbrains.jsonSchema.impl.JsonSchemaReader.LOG;

/**
 * @author Irina.Chernushina on 3/28/2016.
 */
public class JsonSchemaExportedDefinitions {
  private final Map<String, Map<String, JsonSchemaObject>> myMap;
  private final Project myProject;

  public static JsonSchemaExportedDefinitions getInstance(final Project project) {
    return ServiceManager.getService(project, JsonSchemaExportedDefinitions.class);
  }

  public JsonSchemaExportedDefinitions(@NotNull final Project project) {
    myProject = project;
    myMap = Collections.synchronizedMap(new HashMap<>());
  }

  public void register(@NotNull final String url, @NotNull final Map<String, JsonSchemaObject> map) {
    myMap.put(url, map);
    if (myMap.size() > 10000) {
      LOG.info("Too many schema definitions registered. Something could go wrong.");
    }
  }

  public JsonSchemaObject findDefinition(@NotNull final String url, @NotNull final String relativePart, @NotNull final JsonSchemaObject rootObject) {
    ServiceManager.getService(myProject, JsonSchemaService.class).ensureExportedDefinitionsInitialized();
    final Map<String, JsonSchemaObject> map = myMap.get(url);
    if (map != null) {
      return JsonSchemaReader.findDefinition(myProject, relativePart, rootObject, map, false);
    }
    return null;
  }
}
