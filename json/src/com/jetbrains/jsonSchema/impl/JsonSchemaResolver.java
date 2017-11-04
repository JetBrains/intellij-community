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

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.impl.JsonSchemaAnnotatorChecker.areSchemaTypesCompatible;

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
    final JsonSchemaTreeNode node = JsonSchemaVariantsTreeBuilder.buildTree(mySchema, myPosition, false, false, !myIsName);
    return MatchResult.create(node);
  }

  @NotNull
  public Collection<JsonSchemaObject> resolve() {
    final MatchResult result = detailedResolve();
    final List<JsonSchemaObject> list = new ArrayList<>(result.mySchemas);
    list.addAll(result.myExcludingSchemas.stream().flatMap(Set::stream).collect(Collectors.toSet()));
    return list;
  }

  @Nullable
  public PsiElement findNavigationTarget(boolean literalResolve,
                                         @Nullable final JsonValue element,
                                         boolean acceptAdditionalPropertiesSchema) {
    final JsonSchemaTreeNode node = JsonSchemaVariantsTreeBuilder
      .buildTree(mySchema, myPosition, true, literalResolve, acceptAdditionalPropertiesSchema || !myIsName);
    return getSchemaNavigationItem(selectSchema(node, element, myPosition.isEmpty()));
  }

  @Nullable
  private static JsonSchemaObject selectSchema(@NotNull final JsonSchemaTreeNode resolveRoot,
                                               @Nullable final JsonValue element, boolean topLevelSchema) {
    final MatchResult matchResult = MatchResult.create(resolveRoot);
    List<JsonSchemaObject> schemas = new ArrayList<>(matchResult.mySchemas);
    schemas.addAll(matchResult.myExcludingSchemas.stream().flatMap(Set::stream).collect(Collectors.toSet()));

    final JsonSchemaObject firstSchema = getFirstValidSchema(schemas);
    if (element == null || schemas.size() == 1 || firstSchema == null) {
      return firstSchema;
    }
    // actually we pass any schema here
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, firstSchema);
    JsonValueAdapter adapter;
    if (walker == null || (adapter = walker.createValueAdapter(element)) == null) return null;

    final JsonValueAdapter parentAdapter;
    if (topLevelSchema) {
      parentAdapter = null;
    } else {
      final JsonValue parentValue = PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(element, JsonProperty.class),
                                                                JsonObject.class, JsonArray.class);
      if (parentValue == null || (parentAdapter = walker.createValueAdapter(parentValue)) == null) return null;
    }

    final Ref<JsonSchemaObject> schemaRef = new Ref<>();
    MatchResult.iterateTree(resolveRoot, node -> {
      final JsonSchemaTreeNode parent = node.getParent();
      if (node.getSchema() == null || parentAdapter != null && parent != null && parent.isNothing()) return true;
      if (!isCorrect(adapter, node.getSchema())) return true;
      if (parentAdapter == null ||
          parent == null ||
          parent.getSchema() == null ||
          parent.isAny() ||
          isCorrect(parentAdapter, parent.getSchema())) {
        schemaRef.set(node.getSchema());
        return false;
      }
      return true;
    });
    return schemaRef.get();
  }

  @Nullable
  private static JsonSchemaObject getFirstValidSchema(List<JsonSchemaObject> schemas) {
    return schemas.stream().filter(schema -> schema.getJsonObject().isValid()).findFirst().orElse(null);
  }

  private static boolean isCorrect(@NotNull final JsonValueAdapter value, @NotNull final JsonSchemaObject schema) {
    if (!schema.getJsonObject().isValid()) return false;
    final JsonSchemaType type = JsonSchemaType.getType(value);
    if (type == null) return true;
    if (!areSchemaTypesCompatible(schema, type)) return false;
    final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker();
    checker.checkByScheme(value, schema);
    return checker.isCorrect();
  }

  @Nullable
  private static JsonValue getSchemaNavigationItem(@Nullable final JsonSchemaObject schema) {
    if (schema == null) return null;
    final JsonObject jsonObject = schema.getJsonObject();
    if (jsonObject.getParent() instanceof JsonProperty) {
      return ((JsonProperty)jsonObject.getParent()).getNameElement();
    }
    return jsonObject;
  }
}
