// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.tree;

import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.SchemaResolveState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder.andGroups;

public final class AllOfOperation extends Operation {
  private final JsonSchemaService myService;

  public AllOfOperation(@NotNull JsonSchemaObject sourceNode, JsonSchemaService service) {
    super(sourceNode);
    myService = service;
  }

  @Override
  public void map(final @NotNull Set<JsonSchemaObject> visited) {
    var allOf = mySourceNode.getAllOf();
    assert allOf != null;
    myChildOperations.addAll(ContainerUtil.map(allOf, sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService)));
  }

  private static <T> int maxSize(List<List<T>> items) {
    if (items.size() == 0) return 0;
    int maxsize = -1;
    for (List<T> item : items) {
      int size = item.size();
      if (maxsize < size) maxsize = size;
    }
    return maxsize;
  }

  @Override
  public void reduce() {
    myAnyOfGroup.add(mySourceNode);

    for (Operation op : myChildOperations) {
      if (!op.myState.equals(SchemaResolveState.normal)) continue;

      final List<? extends JsonSchemaObject> mergedAny = andGroups(op.myAnyOfGroup, myAnyOfGroup);

      final List<List<JsonSchemaObject>> mergedExclusive =
        new ArrayList<>(op.myAnyOfGroup.size() * maxSize(myOneOfGroup) +
                        myAnyOfGroup.size() * maxSize(op.myOneOfGroup) +
                        maxSize(myOneOfGroup) * maxSize(op.myOneOfGroup));

      for (var objects : myOneOfGroup) {
        mergedExclusive.add(andGroups(op.myAnyOfGroup, objects));
      }
      for (var objects : op.myOneOfGroup) {
        mergedExclusive.add(andGroups(objects, myAnyOfGroup));
      }
      for (var group : op.myOneOfGroup) {
        for (var otherGroup : myOneOfGroup) {
          mergedExclusive.add(andGroups(group, otherGroup));
        }
      }

      myAnyOfGroup.clear();
      myOneOfGroup.clear();
      myAnyOfGroup.addAll(mergedAny);
      myOneOfGroup.addAll(mergedExclusive);
    }
  }
}
