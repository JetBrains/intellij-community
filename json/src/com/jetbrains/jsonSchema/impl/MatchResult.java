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

import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Irina.Chernushina on 4/22/2017.
 */
public class MatchResult {
  boolean myAny;
  boolean myNothing;
  public final List<JsonSchemaObject> mySchemas;
  public final List<JsonSchemaObject> myExcludingSchemas;

  private MatchResult() {
    mySchemas = new SmartList<>();
    myExcludingSchemas = new SmartList<>();
  }

  public static MatchResult zipTree(@NotNull JsonSchemaTreeNode root) {
    final MatchResult result = new MatchResult();
    ContainerUtil.process(new JBTreeTraverser<JsonSchemaTreeNode>(node -> node.getChildren()).withRoot(root).preOrderDfsTraversal(),
                          node -> {
                            result.myAny |= node.isAny();
                            result.myNothing |= node.isNothing();
                            if (node.getChildren().isEmpty() && !node.isAny() && !node.isNothing() && !node.isConflicting()
                                && !node.isDefinitionNotFound()) {
                              if (node.getParent() != null && node.getParent().getExcludingChildren().contains(node)) {
                                result.myExcludingSchemas.add(node.getSchema());
                              } else {
                                result.mySchemas.add(node.getSchema());
                              }
                            }
                            return true;
                          });
    return result;
  }
}
