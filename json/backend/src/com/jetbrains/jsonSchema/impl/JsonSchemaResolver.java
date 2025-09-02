// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.tree.JsonSchemaNodeExpansionRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static com.jetbrains.jsonSchema.impl.JsonSchemaValidityCacheKt.getOrComputeAdapterValidityAgainstGivenSchema;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.*;

public final class JsonSchemaResolver {
  private final @NotNull Project myProject;
  private final @NotNull JsonSchemaObject mySchema;
  private final @NotNull JsonPointerPosition myPosition;
  private final @NotNull JsonSchemaNodeExpansionRequest myExpansionRequest;


  /**
   * @deprecated Use com.jetbrains.jsonSchema.impl.JsonSchemaResolver#JsonSchemaResolver(
   *  com.intellij.openapi.project.Project,
   *  com.jetbrains.jsonSchema.impl.JsonSchemaObject,
   *  com.intellij.json.pointer.JsonPointerPosition,
   *  com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
   * )
   */
  @Deprecated(forRemoval = true)
  public JsonSchemaResolver(@NotNull Project project,
                            @NotNull JsonSchemaObject schema,
                            @NotNull JsonPointerPosition position) {
    this(project, schema, position, new JsonSchemaNodeExpansionRequest(null, true));
  }

  public JsonSchemaResolver(@NotNull Project project,
                            @NotNull JsonSchemaObject schema,
                            @NotNull JsonPointerPosition position,
                            @Nullable JsonValueAdapter inspectedValueAdapter) {
    this(project, schema, position, new JsonSchemaNodeExpansionRequest(inspectedValueAdapter, true));
  }

  public JsonSchemaResolver(@NotNull Project project,
                            @NotNull JsonSchemaObject schema,
                            @NotNull JsonPointerPosition position,
                            @NotNull JsonSchemaNodeExpansionRequest expansionRequest) {
    myProject = project;
    mySchema = schema;
    myPosition = position;
    myExpansionRequest = expansionRequest;
  }

  public MatchResult detailedResolve() {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.JsonSchemaResolveTreeBuild);
    final JsonSchemaTreeNode node = JsonSchemaVariantsTreeBuilder.buildTree(myProject, myExpansionRequest, mySchema, myPosition, false);
    return MatchResult.create(node);
  }

  public @NotNull Collection<JsonSchemaObject> resolve() {
    final MatchResult result = detailedResolve();
    final List<JsonSchemaObject> list = new LinkedList<>();
    list.addAll(result.mySchemas);
    for (Collection<? extends JsonSchemaObject> myExcludingSchema : result.myExcludingSchemas) {
      list.addAll(myExcludingSchema);
    }
    return list;
  }

  public @Nullable PsiElement findNavigationTarget(final @Nullable PsiElement element) {
    final JsonSchemaTreeNode node = JsonSchemaVariantsTreeBuilder
      .buildTree(myProject, myExpansionRequest, mySchema, myPosition, true);
    final JsonSchemaObject schema = selectSchema(node, element, myPosition.isEmpty());
    if (schema == null) return null;
    VirtualFile file = JsonSchemaService.Impl.get(myProject).resolveSchemaFile(schema);
    if (file == null) return null;
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) return null;
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(psiFile, schema);
    return walker == null ? null : resolvePosition(walker, psiFile, JsonPointerPosition.parsePointer(schema.getPointer()));
  }

  public static @Nullable PsiElement resolvePosition(@NotNull JsonLikePsiWalker walker,
                                                      @Nullable PsiElement element,
                                                      @NotNull JsonPointerPosition position) {
    PsiElement psiElement = element instanceof PsiFile ? ContainerUtil.getFirstItem(walker.getRoots((PsiFile)element)) : element;
    if (psiElement == null) return null;
    JsonValueAdapter value = walker.createValueAdapter(psiElement);
    while (position != null && !position.isEmpty()) {
      if (value instanceof JsonObjectValueAdapter) {
        String name = position.getFirstName();
        if (name == null) return null;
        JsonPropertyAdapter property = findProperty((JsonObjectValueAdapter)value, name);
        if (property != null) {
          value = getValue(property);
          if (value == null) return null;
        }
        else {
          JsonPropertyAdapter props = findProperty((JsonObjectValueAdapter)value, JSON_PROPERTIES);
          if (props != null) {
            value = getValue(props);
            continue;
          }

          JsonPropertyAdapter defs = findProperty((JsonObjectValueAdapter)value, JSON_DEFINITIONS);
          if (defs != null) {
            value = getValue(defs);
            continue;
          }

          JsonPropertyAdapter defs9 = findProperty((JsonObjectValueAdapter)value, DEFS);
          if (defs9 != null) {
            value = getValue(defs9);
            continue;
          }
          return null;
        }
      }
      else if (value instanceof JsonArrayValueAdapter) {
        int index = position.getFirstIndex();
        if (index >= 0) {
          List<JsonValueAdapter> values = ((JsonArrayValueAdapter)value).getElements();
          if (values.size() > index) {
            value = values.get(index);
          }
          else {
            return null;
          }
        }
      }
      position = position.skip(1);
    }
    if (value == null) {
      return null;
    }

    PsiElement delegate = value.getDelegate();
    PsiElement propertyNameElement = walker.getPropertyNameElement(delegate.getParent());
    return propertyNameElement == null ? delegate : propertyNameElement;
  }

  private static @Nullable JsonValueAdapter getValue(@NotNull JsonPropertyAdapter property) {
    Collection<JsonValueAdapter> values = property.getValues();
    return values.size() == 1 ? values.iterator().next() : null;
  }

  private static @Nullable JsonPropertyAdapter findProperty(@NotNull JsonObjectValueAdapter value, @NotNull String name) {
    List<JsonPropertyAdapter> list = value.getPropertyList();
    return ContainerUtil.find(list, p -> name.equals(p.getName()));
  }

  public static @Nullable JsonSchemaObject selectSchema(final @NotNull JsonSchemaTreeNode resolveRoot,
                                                  final @Nullable PsiElement element,
                                                  boolean topLevelSchema) {
    final MatchResult matchResult = MatchResult.create(resolveRoot);
    List<JsonSchemaObject> schemas = new ArrayList<>(matchResult.mySchemas);
    schemas.addAll(matchResult.myExcludingSchemas.stream().flatMap(Collection::stream).toList());

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
      final PsiElement parentValue = walker.getParentContainer(element);
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

  private static @Nullable JsonSchemaObject getFirstValidSchema(List<JsonSchemaObject> schemas) {
    return schemas.stream().findFirst().orElse(null);
  }

  public static boolean isCorrect(final @NotNull JsonValueAdapter value, final @NotNull JsonSchemaObject schema) {
    return getOrComputeAdapterValidityAgainstGivenSchema(value, schema);
  }
}
