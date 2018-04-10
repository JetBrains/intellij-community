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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 4/20/2017.
 */
public class JsonSchemaTreeNode {
  private boolean myAny;
  private boolean myNothing;
  private int myExcludingGroupNumber = -1;
  @NotNull private SchemaResolveState myResolveState = SchemaResolveState.normal;

  @Nullable private final JsonSchemaObject mySchema;
  @NotNull private final List<JsonSchemaVariantsTreeBuilder.Step> mySteps = new SmartList<>();

  @Nullable private final JsonSchemaTreeNode myParent;
  @NotNull private final List<JsonSchemaTreeNode> myChildren = new ArrayList<>();

  public JsonSchemaTreeNode(@Nullable JsonSchemaTreeNode parent,
                            @Nullable JsonSchemaObject schema) {
    assert schema != null || parent != null;
    myParent = parent;
    mySchema = schema;
    if (parent != null && !parent.getSteps().isEmpty()) {
      mySteps.addAll(parent.getSteps().subList(1, parent.getSteps().size()));
    }
  }

  public void anyChild() {
    final JsonSchemaTreeNode node = new JsonSchemaTreeNode(this, null);
    node.myAny = true;
    myChildren.add(node);
  }

  public void nothingChild() {
    final JsonSchemaTreeNode node = new JsonSchemaTreeNode(this, null);
    node.myNothing = true;
    myChildren.add(node);
  }

  public void createChildrenFromOperation(@NotNull JsonSchemaVariantsTreeBuilder.Operation operation) {
    if (!SchemaResolveState.normal.equals(operation.myState)) {
      final JsonSchemaTreeNode node = new JsonSchemaTreeNode(this, null);
      node.myResolveState = operation.myState;
      myChildren.add(node);
      return;
    }
    if (!operation.myAnyOfGroup.isEmpty()) {
      myChildren.addAll(convertToNodes(operation.myAnyOfGroup));
    }
    if (!operation.myOneOfGroup.isEmpty()) {
      for (int i = 0; i < operation.myOneOfGroup.size(); i++) {
        final List<JsonSchemaObject> group = operation.myOneOfGroup.get(i);
        final List<JsonSchemaTreeNode> children = convertToNodes(group);
        final int number = i;
        children.forEach(c -> c.myExcludingGroupNumber = number);
        myChildren.addAll(children);
      }
    }
  }

  private List<JsonSchemaTreeNode> convertToNodes(List<JsonSchemaObject> children) {
    return children.stream().map(s -> new JsonSchemaTreeNode(this, s)).collect(Collectors.toList());
  }

  @NotNull
  public SchemaResolveState getResolveState() {
    return myResolveState;
  }

  public boolean isAny() {
    return myAny;
  }

  public boolean isNothing() {
    return myNothing;
  }


  public void setChild(@NotNull final JsonSchemaObject schema) {
    myChildren.add(new JsonSchemaTreeNode(this, schema));
  }

  @Nullable
  public JsonSchemaObject getSchema() {
    return mySchema;
  }

  @NotNull
  public List<JsonSchemaVariantsTreeBuilder.Step> getSteps() {
    return mySteps;
  }

  @Nullable
  public JsonSchemaTreeNode getParent() {
    return myParent;
  }

  @NotNull
  public List<JsonSchemaTreeNode> getChildren() {
    return myChildren;
  }

  public int getExcludingGroupNumber() {
    return myExcludingGroupNumber;
  }

  public void setSteps(@NotNull List<JsonSchemaVariantsTreeBuilder.Step> steps) {
    mySteps.clear();
    mySteps.addAll(steps);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JsonSchemaTreeNode node = (JsonSchemaTreeNode)o;

    if (myAny != node.myAny) return false;
    if (myNothing != node.myNothing) return false;
    if (myResolveState != node.myResolveState) return false;
    if (mySchema != null ? !mySchema.equals(node.mySchema) : node.mySchema != null) return false;
    //noinspection RedundantIfStatement
    if (!mySteps.equals(node.mySteps)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myAny ? 1 : 0);
    result = 31 * result + (myNothing ? 1 : 0);
    result = 31 * result + myResolveState.hashCode();
    result = 31 * result + (mySchema != null ? mySchema.hashCode() : 0);
    result = 31 * result + mySteps.hashCode();
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("NODE#" + hashCode() + "\n");
    sb.append(mySteps.stream().map(Object::toString).collect(Collectors.joining("->", "steps: <", ">")));
    sb.append("\n");
    if (myExcludingGroupNumber >= 0) sb.append("in excluding group\n");
    if (myAny) sb.append("any");
    else if (myNothing) sb.append("nothing");
    else if (!SchemaResolveState.normal.equals(myResolveState)) sb.append(myResolveState.name());
    else {
      assert mySchema != null;
      final String name = mySchema.getSchemaFile().getName();
      sb.append("schema from file: ").append(name).append("\n");
      if (mySchema.getRef() != null) sb.append("$ref: ").append(mySchema.getRef()).append("\n");
      else if (!mySchema.getProperties().isEmpty()) {
        sb.append("properties: ");
        sb.append(mySchema.getProperties().keySet().stream().collect(Collectors.joining(", "))).append("\n");
      }
      if (!myChildren.isEmpty()) {
        sb.append("OR children of NODE#").append(hashCode()).append(":\n----------------\n")
          .append(myChildren.stream().map(Object::toString).collect(Collectors.joining("\n")))
          .append("\n=================\n");
      }
    }
    return sb.toString();
  }
}
