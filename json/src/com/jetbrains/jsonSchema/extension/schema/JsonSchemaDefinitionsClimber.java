/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.jsonSchema.extension.schema;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaReader;
import com.jetbrains.jsonSchema.impl.JsonSchemaWalker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.jsonSchema.extension.schema.JsonSchemaInsideSchemaResolver.PROPERTIES;

/**
 * @author Irina.Chernushina on 1/10/2017.
 */
public class JsonSchemaDefinitionsClimber {
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile mySchemaFile;
  @NotNull private final String myShortPropertyName;
  @NotNull private final List<JsonSchemaWalker.Step> myPosition;
  @NotNull private final PairConsumer<VirtualFile, String> myConsumer;

  public JsonSchemaDefinitionsClimber(@NotNull Project project,
                                      @NotNull VirtualFile schemaFile,
                                      @NotNull String shortPropertyName,
                                      @NotNull List<JsonSchemaWalker.Step> position,
                                      @NotNull PairConsumer<VirtualFile, String> consumer) {
    myProject = project;
    mySchemaFile = schemaFile;
    myShortPropertyName = shortPropertyName;
    myPosition = position.subList(0, position.size() - 1);
    myConsumer = consumer;
  }

  public void iterateMatchingDefinitions() {
    final JsonSchemaWalker.CompletionSchemesConsumer consumer = new JsonSchemaWalker.CompletionSchemesConsumer() {
      @Override
      public void consume(boolean isName, @NotNull JsonSchemaObject schema) {
        processDefinitionAddress(schema, myShortPropertyName);

        List<JsonSchemaObject> list = new ArrayList<>();
        if (schema.getAllOf() != null) list.addAll(schema.getAllOf());
        if (schema.getAnyOf() != null) list.addAll(schema.getAnyOf());
        if (schema.getOneOf() != null) list.addAll(schema.getOneOf());
        for (JsonSchemaObject schemaObject : list) {
          processDefinitionAddress(schemaObject, myShortPropertyName);
        }
      }
    };

    JsonSchemaService.Impl.getEx(myProject).visitSchemaObject(
      mySchemaFile,
      object -> {
        if (myPosition.isEmpty()) {
          consumer.consume(true, object);
          return true;
        }
        JsonSchemaWalker.extractSchemaVariants(consumer, object, true, myPosition);
        return true;
      });
  }

  private void processDefinitionAddress(@NotNull JsonSchemaObject parSchema, @NotNull String propertyName) {
    final String definitionAddress = parSchema.getDefinitionAddress();
    if (StringUtil.isEmptyOrSpaces(definitionAddress)) return;

    final JsonSchemaReader.SchemaUrlSplitter splitter = new JsonSchemaReader.SchemaUrlSplitter(definitionAddress);

    if (!splitter.isAbsolute()) {
      VirtualFile schemaFile = mySchemaFile;
      if (parSchema.getId() != null) {
        schemaFile = JsonSchemaService.Impl.getEx(myProject).getSchemaFileById(parSchema.getId(), mySchemaFile);
        if (schemaFile == null) return;
      }
      final String newReferenceName = definitionAddress.substring(1) + PROPERTIES + propertyName;
      myConsumer.consume(schemaFile, newReferenceName);
    } else {
      String relative = splitter.getRelativePath();
      if (StringUtil.isEmptyOrSpaces(relative)) {
        relative = PROPERTIES + propertyName;
      } else {
        relative += ((relative.endsWith("/") ? PROPERTIES.substring(1) : PROPERTIES) + propertyName);
      }
      assert splitter.getSchemaId() != null;

      final VirtualFile schemaFile = JsonSchemaService.Impl.getEx(myProject).getSchemaFileById(parSchema.getId(), mySchemaFile);
      if (schemaFile == null) return;
      myConsumer.consume(schemaFile, relative);
    }
  }
}
