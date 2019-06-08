// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.pointer.JsonPointerPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
  @NotNull private final JsonPointerPosition myPosition;

  @Nullable private final JsonSchemaTreeNode myParent;
  @NotNull private final List<JsonSchemaTreeNode> myChildren = new ArrayList<>();

  public JsonSchemaTreeNode(@Nullable JsonSchemaTreeNode parent,
                            @Nullable JsonSchemaObject schema) {
    assert schema != null || parent != null;
    myParent = parent;
    mySchema = schema;
    final JsonPointerPosition steps = parent != null ? parent.getPosition().skip(1) : null;
    myPosition = steps == null ? new JsonPointerPosition() : steps;
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
    List<JsonSchemaTreeNode> nodes = new ArrayList<>(children.size());
    for (JsonSchemaObject child: children) {
      nodes.add(new JsonSchemaTreeNode(this, child));
    }
    return nodes;
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
  public JsonPointerPosition getPosition() {
    return myPosition;
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

  public void setPosition(@NotNull JsonPointerPosition steps) {
    myPosition.updateFrom(steps);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JsonSchemaTreeNode node = (JsonSchemaTreeNode)o;

    if (myAny != node.myAny) return false;
    if (myNothing != node.myNothing) return false;
    if (myResolveState != node.myResolveState) return false;
    if (!Objects.equals(mySchema, node.mySchema)) return false;
    //noinspection RedundantIfStatement
    if (!myPosition.equals(node.myPosition)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myAny ? 1 : 0);
    result = 31 * result + (myNothing ? 1 : 0);
    result = 31 * result + myResolveState.hashCode();
    result = 31 * result + (mySchema != null ? mySchema.hashCode() : 0);
    result = 31 * result + myPosition.hashCode();
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("NODE#" + hashCode() + "\n");
    sb.append(myPosition.toString());
    sb.append("\n");
    if (myExcludingGroupNumber >= 0) sb.append("in excluding group\n");
    if (myAny) sb.append("any");
    else if (myNothing) sb.append("nothing");
    else if (!SchemaResolveState.normal.equals(myResolveState)) sb.append(myResolveState.name());
    else {
      assert mySchema != null;
      sb.append("schema").append("\n");
      if (mySchema.getRef() != null) sb.append("$ref: ").append(mySchema.getRef()).append("\n");
      else if (!mySchema.getProperties().isEmpty()) {
        sb.append("properties: ");
        sb.append(String.join(", ", mySchema.getProperties().keySet())).append("\n");
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
