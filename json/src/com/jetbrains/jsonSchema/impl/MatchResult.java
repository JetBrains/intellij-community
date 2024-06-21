// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.util.Processor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class MatchResult {
  public final List<JsonSchemaObject> mySchemas;
  public final List<Collection<? extends JsonSchemaObject>> myExcludingSchemas;

  private MatchResult(final @NotNull List<JsonSchemaObject> schemas, final @NotNull List<Collection<? extends JsonSchemaObject>> excludingSchemas) {
    mySchemas = Collections.unmodifiableList(schemas);
    myExcludingSchemas = Collections.unmodifiableList(excludingSchemas);
  }

  public static MatchResult create(@NotNull JsonSchemaTreeNode root) {
    List<JsonSchemaObject> schemas = new ArrayList<>();
    Int2ObjectMap<List<JsonSchemaObject>> oneOfGroups = new Int2ObjectOpenHashMap<>();
    iterateTree(root, node -> {
      if (node.isAny()) return true;
      int groupNumber = node.getExcludingGroupNumber();
      if (groupNumber < 0) {
        schemas.add(node.getSchema());
      }
      else {
        oneOfGroups.computeIfAbsent(groupNumber, __ -> new ArrayList<>()).add(node.getSchema());
      }
      return true;
    });
    List<Collection<? extends JsonSchemaObject>> result;
    if (oneOfGroups.isEmpty()) {
      result = Collections.emptyList();
    }
    else {
      result = new ArrayList<>(oneOfGroups.values());
    }
    return new MatchResult(schemas, result);
  }

  public static void iterateTree(@NotNull JsonSchemaTreeNode root,
                                 final @NotNull Processor<? super JsonSchemaTreeNode> processor) {
    final ArrayDeque<JsonSchemaTreeNode> queue = new ArrayDeque<>(root.getChildren());
    while (!queue.isEmpty()) {
      final JsonSchemaTreeNode node = queue.removeFirst();
      if (node.getChildren().isEmpty()) {
        if (!node.isNothing() && SchemaResolveState.normal.equals(node.getResolveState()) && !processor.process(node)) {
          break;
        }
      } else {
        queue.addAll(node.getChildren());
      }
    }
  }
}
