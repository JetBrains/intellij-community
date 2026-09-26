// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.tree;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.SchemaResolveState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectMergerKt.getJsonSchemaObjectMerger;

public final class ProcessDefinitionsOperation extends Operation {
  private final JsonSchemaService myService;

  public ProcessDefinitionsOperation(@NotNull JsonSchemaObject sourceNode,
                                     @NotNull JsonSchemaService service,
                                     @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
    super(sourceNode, expansionRequest);
    myService = service;
  }

  @Override
  public void map(final @NotNull Set<JsonSchemaObject> visited) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.DefinitionsExpanded);
    var current = mySourceNode;
    while (!StringUtil.isEmptyOrSpaces(current.getRef())) {
      ProgressManager.checkCanceled();
      final var definition = current.resolveRefSchema(myService);
      if (definition == null) {
        myState = SchemaResolveState.brokenDefinition;
        return;
      }
      // this definition was already expanded; do not cycle
      if (!visited.add(definition)) break;
      current = getJsonSchemaObjectMerger().mergeObjects(current, definition, current);
    }
    final Operation expandOperation = createExpandOperation(current, myService, myExpansionRequest);
    if (expandOperation != null) {
      myChildOperations.add(expandOperation);
    }
    else {
      myAnyOfGroup.add(current);
    }
  }

  @Override
  public void reduce() {
    if (!myChildOperations.isEmpty()) {
      assert myChildOperations.size() == 1;
      final Operation operation = myChildOperations.get(0);
      myAnyOfGroup.addAll(operation.myAnyOfGroup);
      myOneOfGroup.addAll(operation.myOneOfGroup);
    }
  }
}
