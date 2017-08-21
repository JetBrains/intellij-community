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
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 4/24/2017.
 */
public class JsonSchemaResolver {
  @NotNull private final JsonSchemaObject mySchema;
  private final boolean myIsName;
  @NotNull private final List<JsonSchemaVariantsTreeBuilder.Step> myPosition;

  public JsonSchemaResolver(@NotNull JsonSchemaObject schema, boolean isName, @NotNull List<JsonSchemaVariantsTreeBuilder.Step> position) {
    mySchema = schema;
    myIsName = isName;
    myPosition = position;
  }

  public JsonSchemaResolver(@NotNull JsonSchemaObject schema) {
    mySchema = schema;
    myIsName = true;
    myPosition = Collections.emptyList();
  }

  public MatchResult detailedResolve() {
    return detailedResolve(false, false, false);
  }

  private MatchResult detailedResolve(boolean skipLastExpand, boolean literalResolve, boolean acceptAdditionalPropertiesSchema) {
    final JsonSchemaTreeNode node = JsonSchemaVariantsTreeBuilder
      .buildTree(mySchema, myPosition, skipLastExpand, literalResolve, acceptAdditionalPropertiesSchema || !myIsName);
    return MatchResult.create(node);
  }

  @NotNull
  public Collection<JsonSchemaObject> resolve() {
    return resolve(false, false, false);
  }

  private Collection<JsonSchemaObject> resolve(boolean skipLastExpand, boolean literalResolve, boolean acceptAdditionalPropertiesSchema) {
    final MatchResult result = detailedResolve(skipLastExpand, literalResolve, acceptAdditionalPropertiesSchema);
    final List<JsonSchemaObject> list = new ArrayList<>(result.mySchemas);
    list.addAll(result.myExcludingSchemas.stream().flatMap(Set::stream).collect(Collectors.toSet()));
    return list;
  }

  @Nullable
  public PsiElement findNavigationTarget(boolean literalResolve, boolean acceptAdditionalPropertiesSchema) {
    final Collection<JsonSchemaObject> schemas = resolve(true, literalResolve, acceptAdditionalPropertiesSchema);

    return schemas.stream().filter(schema -> schema.getJsonObject().isValid())
      .findFirst()
      .map(schema -> {
        final JsonObject jsonObject = schema.getJsonObject();
        if (jsonObject.getParent() instanceof JsonProperty)
          return ((JsonProperty)jsonObject.getParent()).getNameElement();
        return jsonObject;
      })
      .orElse(null);
  }
}
