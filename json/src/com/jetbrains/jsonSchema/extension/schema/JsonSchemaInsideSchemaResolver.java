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
import com.intellij.util.SmartList;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * todo remove this class
 * @author Irina.Chernushina on 1/10/2017.
 */
public class JsonSchemaInsideSchemaResolver {
  public static final String PROPERTIES = "/properties/";
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile mySchemaFile;
  @NotNull private final List<JsonSchemaWalker.Step> mySteps;

  public JsonSchemaInsideSchemaResolver(@NotNull Project project,
                                        @NotNull VirtualFile schemaFile,
                                        @NotNull List<JsonSchemaWalker.Step> steps) {
    myProject = project;
    mySchemaFile = schemaFile;
    mySteps = steps;
  }

  // todo can be multiple variants resolve
  public PsiElement resolveInSchemaRecursively() {
    final JsonSchemaObject rootSchema = JsonSchemaService.Impl.get(myProject).getSchemaObjectForSchemaFile(mySchemaFile);
    if (rootSchema == null) return null;

    final List<JsonSchemaObject> schemas = new SmartList<>();
    final MatchResult result;
    if (mySteps.isEmpty()) {
      result = JsonSchemaVariantsTreeBuilder.simplify(rootSchema, rootSchema);
    } else {
      final JsonSchemaVariantsTreeBuilder builder = new JsonSchemaVariantsTreeBuilder(rootSchema, true, mySteps);
      final JsonSchemaTreeNode root = builder.buildTree();
      result = MatchResult.zipTree(root);
    }
    schemas.addAll(result.mySchemas);
    schemas.addAll(result.myExcludingSchemas);

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
