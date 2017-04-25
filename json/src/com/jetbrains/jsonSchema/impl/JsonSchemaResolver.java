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

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Irina.Chernushina on 4/24/2017.
 */
public class JsonSchemaResolver {
  @NotNull private final JsonSchemaObject mySchema;
  private final boolean myIsName;
  @NotNull private final List<JsonSchemaVariantsTreeBuilder.Step> myPosition;

  public JsonSchemaResolver(@NotNull JsonSchemaObject schema,
                            boolean isName,
                            @NotNull List<JsonSchemaVariantsTreeBuilder.Step> position) {
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
    final JsonSchemaTreeNode node = new JsonSchemaVariantsTreeBuilder(mySchema, myIsName, myPosition).buildTree();
    return MatchResult.zipTree(node);
  }

  public Collection<JsonSchemaObject> resolve() {
    final MatchResult result = detailedResolve();
    final Set<JsonSchemaObject> set = new HashSet<>(result.mySchemas);
    set.addAll(result.myExcludingSchemas);
    return set;
  }
}
