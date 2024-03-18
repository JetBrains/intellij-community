// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.tree.Operation;
import com.jetbrains.jsonSchema.impl.tree.ProcessDefinitionsOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.jetbrains.jsonSchema.JsonPointerUtil.isSelfReference;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.*;
import static com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectMergerKt.getJsonSchemaObjectMerger;

public final class JsonSchemaVariantsTreeBuilder {

  public static JsonSchemaTreeNode buildTree(@NotNull Project project,
                                             final @NotNull JsonSchemaObject schema,
                                             final @NotNull JsonPointerPosition position,
                                             final boolean skipLastExpand) {
    final JsonSchemaTreeNode root = new JsonSchemaTreeNode(null, schema);
    JsonSchemaService service = JsonSchemaService.Impl.get(project);
    expandChildSchema(root, schema, service);
    // set root's position since this children are just variants of root
    for (JsonSchemaTreeNode treeNode : root.getChildren()) {
      treeNode.setPosition(position);
    }

    final ArrayDeque<JsonSchemaTreeNode> queue = new ArrayDeque<>(root.getChildren());

    while (!queue.isEmpty()) {
      final JsonSchemaTreeNode node = queue.removeFirst();
      if (node.isAny() || node.isNothing() || node.getPosition().isEmpty() || node.getSchema() == null) continue;
      final JsonPointerPosition step = node.getPosition();
      if (!typeMatches(step.isObject(0), node.getSchema())) {
        node.nothingChild();
        continue;
      }
      final Pair<ThreeState, JsonSchemaObject> pair = doSingleStep(step, node.getSchema(), true);
      if (ThreeState.NO.equals(pair.getFirst())) {
        node.nothingChild();
      }
      else if (ThreeState.YES.equals(pair.getFirst())) {
        node.anyChild();
      }
      else {
        // process step results
        assert pair.getSecond() != null;
        if (node.getPosition().size() > 1 || !skipLastExpand) {
          expandChildSchema(node, pair.getSecond(), service);
        }
        else {
          node.setChild(pair.getSecond());
        }
      }

      queue.addAll(node.getChildren());
    }

    return root;
  }

  private static boolean typeMatches(final boolean isObject, final @NotNull JsonSchemaObject schema) {
    final JsonSchemaType requiredType = isObject ? JsonSchemaType._object : JsonSchemaType._array;
    if (schema.getType() != null) {
      return requiredType.equals(schema.getType());
    }
    if (schema.getTypeVariants() != null) {
      for (JsonSchemaType schemaType : schema.getTypeVariants()) {
        if (requiredType.equals(schemaType)) return true;
      }
      return false;
    }
    return true;
  }

  private static void expandChildSchema(@NotNull JsonSchemaTreeNode node,
                                        @NotNull JsonSchemaObject childSchema,
                                        @NotNull JsonSchemaService service) {
    if (interestingSchema(childSchema)) {
      node.createChildrenFromOperation(getOperation(service, childSchema));
    }
    else {
      node.setChild(childSchema);
    }
  }

  private static @NotNull Operation getOperation(@NotNull JsonSchemaService service,
                                                 JsonSchemaObject param) {
    final Operation expand = new ProcessDefinitionsOperation(param, service);
    expand.doMap(new HashSet<>());
    expand.doReduce();
    return expand;
  }

  public static @NotNull Pair<ThreeState, JsonSchemaObject> doSingleStep(@NotNull JsonPointerPosition step,
                                                                         @NotNull JsonSchemaObject parent,
                                                                         boolean processAllBranches) {
    final String name = step.getFirstName();
    if (name != null) {
      return propertyStep(name, parent, processAllBranches);
    }
    else {
      final int index = step.getFirstIndex();
      assert index >= 0;
      return arrayOrNumericPropertyElementStep(index, parent);
    }
  }

  // even if there are no definitions to expand, this object may work as an intermediate node in a tree,
  // connecting oneOf and allOf expansion, for example
  public static List<JsonSchemaObject> andGroups(@NotNull List<? extends JsonSchemaObject> g1,
                                                 @NotNull List<? extends JsonSchemaObject> g2) {
    List<JsonSchemaObject> result = new ArrayList<>(g1.size() * g2.size());
    for (JsonSchemaObject s : g1) {
      result.addAll(andGroup(s, g2));
    }
    return result;
  }

  // here is important, which pointer gets the result: lets make them all different, otherwise two schemas of branches of oneOf would be equal
  public static List<JsonSchemaObject> andGroup(@NotNull JsonSchemaObject object, @NotNull List<? extends JsonSchemaObject> group) {
    List<JsonSchemaObject> list = new ArrayList<>(group.size());
    for (JsonSchemaObject s : group) {
      // fixme here it is crucial to avoid heavy merging just to get single property value
      var schemaObject = getJsonSchemaObjectMerger().mergeObjects(object, s, s);
      if (schemaObject.isValidByExclusion()) {
        list.add(schemaObject);
      }
    }
    return list;
  }


  private static boolean interestingSchema(@NotNull JsonSchemaObject schema) {
    boolean hasAggregators;
    if (Registry.is("json.schema.object.v2")) {
      hasAggregators =
        schema.hasChildNode(ANY_OF) || schema.hasChildNode(ONE_OF) || schema.hasChildNode(ALL_OF) || schema.hasChildNode(IF);
    }
    else {
      hasAggregators = schema.getAnyOf() != null || schema.getOneOf() != null || schema.getAllOf() != null;
    }
    return hasAggregators || schema.getRef() != null || schema.getIfThenElse() != null;
  }


  private static @NotNull Pair<ThreeState, JsonSchemaObject> propertyStep(@NotNull String name,
                                                                          @NotNull JsonSchemaObject parent,
                                                                          boolean processAllBranches) {
    final JsonSchemaObject child = parent.getPropertyByName(name);
    if (child != null) {
      return Pair.create(ThreeState.UNSURE, child);
    }
    final JsonSchemaObject schema = parent.getMatchingPatternPropertySchema(name);
    if (schema != null) {
      return Pair.create(ThreeState.UNSURE, schema);
    }
    if (parent.getAdditionalPropertiesSchema() != null) {
      return Pair.create(ThreeState.UNSURE, parent.getAdditionalPropertiesSchema());
    }

    if (processAllBranches) {
      List<IfThenElse> ifThenElseList = parent.getIfThenElse();
      if (ifThenElseList != null) {
        for (IfThenElse ifThenElse : ifThenElseList) {
          // resolve inside V7 if-then-else conditionals
          JsonSchemaObject childObject;

          // NOTE: do not resolve inside 'if' itself - it is just a condition, but not an actual validation!
          // only 'then' and 'else' branches provide actual validation sources, but not the 'if' branch

          JsonSchemaObject then = ifThenElse.getThen();
          //noinspection Duplicates
          if (then != null) {
            childObject = then.getPropertyByName(name);
            if (childObject != null) {
              return Pair.create(ThreeState.UNSURE, childObject);
            }
          }
          JsonSchemaObject elseBranch = ifThenElse.getElse();
          //noinspection Duplicates
          if (elseBranch != null) {
            childObject = elseBranch.getPropertyByName(name);
            if (childObject != null) {
              return Pair.create(ThreeState.UNSURE, childObject);
            }
          }
        }
      }
    }
    if (Boolean.FALSE.equals(parent.getAdditionalPropertiesAllowed())) {
      return Pair.create(ThreeState.NO, null);
    }
    // by default, additional properties are allowed
    return Pair.create(ThreeState.YES, null);
  }

  private static @NotNull Pair<ThreeState, JsonSchemaObject> arrayOrNumericPropertyElementStep(int idx, @NotNull JsonSchemaObject parent) {
    if (parent.getItemsSchema() != null) {
      return Pair.create(ThreeState.UNSURE, parent.getItemsSchema());
    }
    if (parent.getItemsSchemaList() != null) {
      final var list = parent.getItemsSchemaList();
      if (idx >= 0 && idx < list.size()) {
        return Pair.create(ThreeState.UNSURE, list.get(idx));
      }
    }
    final String keyAsString = String.valueOf(idx);
    var propWithNameOrNull = parent.getPropertyByName(keyAsString);
    if (propWithNameOrNull != null) {
      return Pair.create(ThreeState.UNSURE, propWithNameOrNull);
    }
    final JsonSchemaObject matchingPatternPropertySchema = parent.getMatchingPatternPropertySchema(keyAsString);
    if (matchingPatternPropertySchema != null) {
      return Pair.create(ThreeState.UNSURE, matchingPatternPropertySchema);
    }
    if (parent.getAdditionalItemsSchema() != null) {
      return Pair.create(ThreeState.UNSURE, parent.getAdditionalItemsSchema());
    }
    if (Boolean.FALSE.equals(parent.getAdditionalItemsAllowed())) {
      return Pair.create(ThreeState.NO, null);
    }
    return Pair.create(ThreeState.YES, null);
  }

  public static final class SchemaUrlSplitter {
    private final @Nullable String mySchemaId;
    private final @NotNull String myRelativePath;

    public SchemaUrlSplitter(final @NotNull String ref) {
      if (isSelfReference(ref)) {
        mySchemaId = null;
        myRelativePath = "";
        return;
      }
      if (!ref.startsWith("#/")) {
        int idx = ref.indexOf("#/");
        if (idx == -1) {
          mySchemaId = ref.endsWith("#") ? ref.substring(0, ref.length() - 1) : ref;
          myRelativePath = "";
        }
        else {
          mySchemaId = ref.substring(0, idx);
          myRelativePath = ref.substring(idx);
        }
      }
      else {
        mySchemaId = null;
        myRelativePath = ref;
      }
    }

    public boolean isAbsolute() {
      return mySchemaId != null;
    }

    public @Nullable String getSchemaId() {
      return mySchemaId;
    }

    public @NotNull String getRelativePath() {
      return myRelativePath;
    }
  }
}
