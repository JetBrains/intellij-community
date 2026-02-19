// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.tree;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.SmartList;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.SchemaResolveState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.ALL_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.ANY_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.IF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.ONE_OF;

public abstract class Operation {
  public final @NotNull JsonSchemaNodeExpansionRequest myExpansionRequest;
  public final @NotNull List<JsonSchemaObject> myAnyOfGroup = new SmartList<>();
  public final @NotNull List<List<JsonSchemaObject>> myOneOfGroup = new SmartList<>();
  public final @NotNull List<Operation> myChildOperations;
  public final @NotNull JsonSchemaObject mySourceNode;
  public SchemaResolveState myState = SchemaResolveState.normal;

  protected Operation(@NotNull JsonSchemaObject sourceNode, @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
    mySourceNode = sourceNode;
    myChildOperations = new ArrayList<>();
    myExpansionRequest = expansionRequest == null ?
                         new JsonSchemaNodeExpansionRequest(null, true) : expansionRequest;
  }

  protected abstract void map(@NotNull Set<JsonSchemaObject> visited);
  protected abstract void reduce();

  public void doMap(final @NotNull Set<JsonSchemaObject> visited) {
    map(visited);
    for (Operation operation : myChildOperations) {
      ProgressManager.checkCanceled();
      operation.doMap(visited);
    }
  }

  public void doReduce() {
    if (!SchemaResolveState.normal.equals(myState)) {
      myChildOperations.clear();
      myAnyOfGroup.clear();
      myOneOfGroup.clear();
      return;
    }

    for (Operation myChildOperation : myChildOperations) {
      ProgressManager.checkCanceled();
      myChildOperation.doReduce();
    }
    reduce();
    myChildOperations.clear();
  }

  protected @Nullable Operation createExpandOperation(@NotNull JsonSchemaObject schema,
                                                      @NotNull JsonSchemaService service,
                                                      @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
    Operation forConflict = getOperationForConflict(schema, service, expansionRequest);
    if (forConflict != null) return forConflict;
    if (schema.hasChildNode(ANY_OF)) return new AnyOfOperation(schema, service, expansionRequest);
    if (schema.hasChildNode(ONE_OF)) return new OneOfOperation(schema, service, expansionRequest);
    if (schema.hasChildNode(ALL_OF)) return new AllOfOperation(schema, service, expansionRequest);
    if (schema.hasChildNode(IF)) return new IfThenElseBranchOperation(schema, expansionRequest, service);
    return null;
  }

  private static @Nullable Operation getOperationForConflict(@NotNull JsonSchemaObject schema,
                                                             @NotNull JsonSchemaService service,
                                                             @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
    // in case of several incompatible operations, choose the most permissive one
    var anyOf = schema.hasChildNode(ANY_OF);
    var oneOf = schema.hasChildNode(ONE_OF);
    var allOf = schema.hasChildNode(ALL_OF);

    if (anyOf && (oneOf || allOf)) {
      return new AnyOfOperation(schema, service, expansionRequest) {{
        myState = SchemaResolveState.conflict;
      }};
    }
    else if (oneOf && allOf) {
      return new OneOfOperation(schema, service, expansionRequest) {{
        myState = SchemaResolveState.conflict;
      }};
    }
    return null;
  }

  protected static List<JsonSchemaObject> mergeOneOf(Operation op) {
    return op.myOneOfGroup.stream().flatMap(List::stream).collect(Collectors.toList());
  }
}
