// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.tree;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.SmartList;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaObjectImpl;
import com.jetbrains.jsonSchema.impl.SchemaResolveState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.*;

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

    // lets do that to make the returned object smaller
    myAnyOfGroup.forEach(Operation::clearVariants);
    myOneOfGroup.forEach(list -> list.forEach(Operation::clearVariants));

    for (Operation myChildOperation : myChildOperations) {
      ProgressManager.checkCanceled();
      myChildOperation.doReduce();
    }
    reduce();
    myChildOperations.clear();
  }

  private static void clearVariants(@NotNull JsonSchemaObject object) {
    if (!(object instanceof JsonSchemaObjectImpl cst)) {
      return;
    }
    cst.setAllOf(null);
    cst.setAnyOf(null);
    cst.setOneOf(null);
  }

  protected @Nullable Operation createExpandOperation(@NotNull JsonSchemaObject schema,
                                                      @NotNull JsonSchemaService service,
                                                      @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
    Operation forConflict = getOperationForConflict(schema, service, expansionRequest);
    if (forConflict != null) return forConflict;
    if (Registry.is("json.schema.object.v2") && schema.hasChildNode(ANY_OF) || schema.getAnyOf() != null) return new AnyOfOperation(schema, service, expansionRequest);
    if (Registry.is("json.schema.object.v2") && schema.hasChildNode(ONE_OF) || schema.getOneOf() != null) return new OneOfOperation(schema, service, expansionRequest);
    if (Registry.is("json.schema.object.v2") && schema.hasChildNode(ALL_OF) || schema.getAllOf() != null) return new AllOfOperation(schema, service, expansionRequest);
    if (Registry.is("json.schema.object.v2") && schema.hasChildNode(IF) || schema.getIfThenElse() != null) return new IfThenElseBranchOperation(schema, expansionRequest, service);
    return null;
  }

  private static @Nullable Operation getOperationForConflict(@NotNull JsonSchemaObject schema,
                                                             @NotNull JsonSchemaService service,
                                                             @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
    // in case of several incompatible operations, choose the most permissive one
    var anyOf = Registry.is("json.schema.object.v2") && schema.hasChildNode(ANY_OF) || schema.getAnyOf() != null;
    var oneOf = Registry.is("json.schema.object.v2") && schema.hasChildNode(ONE_OF) || schema.getOneOf() != null;
    var allOf = Registry.is("json.schema.object.v2") && schema.hasChildNode(ALL_OF) || schema.getAllOf() != null;


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
