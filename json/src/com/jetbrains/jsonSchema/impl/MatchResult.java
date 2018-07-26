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

import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Irina.Chernushina on 4/22/2017.
 */
public class MatchResult {
  public final List<JsonSchemaObject> mySchemas;
  public final List<Collection<? extends JsonSchemaObject>> myExcludingSchemas;

  private MatchResult(@NotNull final List<JsonSchemaObject> schemas, @NotNull final List<Collection<? extends JsonSchemaObject>> excludingSchemas) {
    mySchemas = Collections.unmodifiableList(schemas);
    myExcludingSchemas = Collections.unmodifiableList(excludingSchemas);
  }

  public static MatchResult create(@NotNull JsonSchemaTreeNode root) {
    List<JsonSchemaObject> schemas = new ArrayList<>();
    MultiMap<Integer, JsonSchemaObject> oneOfGroups = MultiMap.create();
    iterateTree(root, node -> {
      if (node.isAny()) return true;
      int groupNumber = node.getExcludingGroupNumber();
      if (groupNumber < 0) {
        schemas.add(node.getSchema());
      }
      else {
        oneOfGroups.putValue(groupNumber, node.getSchema());
      }
      return true;
    });
    List<Collection<? extends JsonSchemaObject>> result = oneOfGroups.isEmpty()
                                                          ? ContainerUtil.emptyList()
                                                          : ContainerUtil.newArrayListWithCapacity(oneOfGroups.keySet().size());
    for (Map.Entry<Integer, Collection<JsonSchemaObject>> entry: oneOfGroups.entrySet()) {
      result.add(entry.getValue());
    }
    return new MatchResult(schemas, result);
  }

  public static void iterateTree(@NotNull JsonSchemaTreeNode root,
                                 @NotNull final Processor<? super JsonSchemaTreeNode> processor) {
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
