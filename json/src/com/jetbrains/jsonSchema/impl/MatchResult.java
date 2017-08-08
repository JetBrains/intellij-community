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

import com.intellij.util.containers.ContainerUtil;
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
    final List<JsonSchemaObject> schemas = new ArrayList<>();
    final Map<Integer, Set<JsonSchemaObject>> oneOfGroups = new HashMap<>();
    ContainerUtil.process(new JBTreeTraverser<JsonSchemaTreeNode>(node -> node.getChildren()).withRoot(root).preOrderDfsTraversal(),
                          node -> {
                            if (node.getChildren().isEmpty() && !node.isAny() && !node.isNothing() &&
                                SchemaResolveState.normal.equals(node.getResolveState())) {
                              final int groupNumber = node.getExcludingGroupNumber();
                              if (groupNumber < 0) {
                                schemas.add(node.getSchema());
                              }
                              else {
                                Set<JsonSchemaObject> set = oneOfGroups.get(groupNumber);
                                if (set == null) oneOfGroups.put(groupNumber, (set = new HashSet<>()));
                                set.add(node.getSchema());
                              }
                            }
                            return true;
                          });
    return new MatchResult(schemas, new ArrayList<>(oneOfGroups.values()));
  }
}
