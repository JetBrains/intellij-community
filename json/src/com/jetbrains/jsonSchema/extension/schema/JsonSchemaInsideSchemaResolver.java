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

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * todo remove this class
 * @author Irina.Chernushina on 1/10/2017.
 */
public class JsonSchemaInsideSchemaResolver {
  public static final String PROPERTIES = "/properties/";
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile mySchemaFile;
  @NotNull private final List<JsonSchemaVariantsTreeBuilder.Step> mySteps;

  public JsonSchemaInsideSchemaResolver(@NotNull Project project,
                                        @NotNull VirtualFile schemaFile,
                                        @NotNull List<JsonSchemaVariantsTreeBuilder.Step> steps) {
    myProject = project;
    mySchemaFile = schemaFile;
    mySteps = steps;
  }

  // todo can be multiple variants resolve
  public PsiElement resolveInSchemaRecursively(@NotNull PsiElement element) {
    final JsonSchemaObject rootSchema = JsonSchemaService.Impl.get(myProject).getSchemaObjectForSchemaFile(mySchemaFile);
    if (rootSchema == null) return null;

    final Collection<JsonSchemaObject> schemas = new JsonSchemaResolver(rootSchema, true, mySteps).resolve();

    return schemas.stream().filter(schema -> schema.getPeerPointer().getElement() != null && schema.getPeerPointer().getElement().isValid())
      .findFirst()
      .map(schema -> {
        final JsonObject jsonObject = schema.getPeerPointer().getElement();
          if (jsonObject != null && jsonObject.getParent() instanceof JsonProperty)
            return ((JsonProperty)jsonObject.getParent()).getNameElement();
          return jsonObject;
      })
      .orElse(null);
  }
}
