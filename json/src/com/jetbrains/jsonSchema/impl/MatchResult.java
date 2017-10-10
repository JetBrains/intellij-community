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

import com.intellij.openapi.util.Ref;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Irina.Chernushina on 4/22/2017.
 */
public class MatchResult {
  public final List<JsonSchemaObject> mySchemas;
  public final List<Set<JsonSchemaObject>> myExcludingSchemas;

  private MatchResult(@NotNull final List<JsonSchemaObject> schemas, @NotNull final List<Set<JsonSchemaObject>> excludingSchemas) {
    mySchemas = Collections.unmodifiableList(schemas);
    myExcludingSchemas = Collections.unmodifiableList(excludingSchemas);
  }

  public static MatchResult create(@NotNull JsonSchemaTreeNode root) {
    List<JsonSchemaObject> schemas = new ArrayList<>();
    Map<Integer, Set<JsonSchemaObject>> oneOfGroups = new HashMap<>();
    iterateTree(root, (parent, node) -> {
      if (node.isAny()) return true;
      int groupNumber = node.getExcludingGroupNumber();
      if (groupNumber < 0) {
        schemas.add(node.getSchema());
      }
      else {
        Set<JsonSchemaObject> set = oneOfGroups.get(groupNumber);
        if (set == null) oneOfGroups.put(groupNumber, (set = new HashSet<>()));
        set.add(node.getSchema());
      }
      return true;
    });
    return new MatchResult(schemas, new ArrayList<>(oneOfGroups.values()));
  }

  public static void iterateTree(@NotNull JsonSchemaTreeNode root,
                                 @NotNull final PairProcessor<JsonSchemaTreeNode, JsonSchemaTreeNode> parentChildConsumer) {
    final Ref<JsonSchemaTreeNode> parentRef = new Ref<>(root);
    JBTreeTraverser.<JsonSchemaTreeNode>from(node -> node.getChildren())
      .withRoot(root)
      .preOrderDfsTraversal()
      .processEach(node -> {
        if (!node.getChildren().isEmpty()) {
          parentRef.set(node);
          return true;
        }
        if (node.getChildren().isEmpty() && !node.isNothing() && SchemaResolveState.normal.equals(node.getResolveState())) {
          assert !parentRef.isNull();
          return parentChildConsumer.process(parentRef.get(), node);
        }
        return true;
      });
  }
}
