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

import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 4/20/2017.
 */
public class JsonSchemaTreeNode {
  @Nullable
  private final JsonSchemaTreeNode myParent;

  private boolean myAny;
  private boolean myNothing;
  private boolean myConflicting;
  private boolean myDefinitionNotFound;

  // todo find a better solution
  @NotNull
  private final JsonSchemaObject myRoot;
  @NotNull
  private final JsonSchemaObject mySchema;

  @Nullable
  private List<JsonSchemaVariantsTreeBuilder.Step> mySteps;

  private List<JsonSchemaTreeNode> myChildren = new ArrayList<>();
  private Set<JsonSchemaTreeNode> myExcludingChildren = new HashSet<>();

  public JsonSchemaTreeNode(@Nullable JsonSchemaTreeNode parent,
                            @NotNull JsonSchemaObject schema) {
    myRoot = parent == null ? schema : parent.getRoot();
    myParent = parent;
    mySchema = schema;
    if (parent == null || parent.getSteps() == null || parent.getSteps().isEmpty()) {
      mySteps = null;
    } else {
      mySteps = parent.getSteps().subList(1, parent.getSteps().size());
    }
  }

  public static JsonSchemaTreeNode createAny(@NotNull JsonSchemaTreeNode parent) {
    final JsonSchemaTreeNode node = new JsonSchemaTreeNode(parent);
    node.any();
    return node;
  }

  public static JsonSchemaTreeNode createNothing(@NotNull JsonSchemaTreeNode parent) {
    final JsonSchemaTreeNode node = new JsonSchemaTreeNode(parent);
    node.nothing();
    return node;
  }

  public static JsonSchemaTreeNode createConflicting(@NotNull JsonSchemaTreeNode parent) {
    final JsonSchemaTreeNode node = new JsonSchemaTreeNode(parent);
    node.conflicting();
    return node;
  }

  public static JsonSchemaTreeNode createNoDefinition(@NotNull JsonSchemaTreeNode parent) {
    final JsonSchemaTreeNode node = new JsonSchemaTreeNode(parent);
    node.definitionNotFound();
    return node;
  }

  private JsonSchemaTreeNode(@NotNull JsonSchemaTreeNode parent) {
    myParent = parent;
    myRoot = parent.getRoot();
    mySteps = null;
    //!!
    mySchema = parent.getSchema();
  }

  public void setChild(JsonSchemaTreeNode child) {
    myChildren.add(child);
  }

  public void addChildren(List<JsonSchemaTreeNode> children) {
    myChildren.addAll(children);
  }

  public void addExcludingChildren(List<JsonSchemaTreeNode> excludingChildrenGroup) {
    myChildren.addAll(excludingChildrenGroup);
    myExcludingChildren.addAll(excludingChildrenGroup);
  }

  @Nullable
  public JsonSchemaTreeNode getParent() {
    return myParent;
  }

  public boolean isAny() {
    return myAny;
  }

  public boolean isNothing() {
    return myNothing;
  }

  @NotNull
  public JsonSchemaObject getSchema() {
    return mySchema;
  }

  @Nullable
  public List<JsonSchemaVariantsTreeBuilder.Step> getSteps() {
    return mySteps;
  }

  public List<JsonSchemaTreeNode> getChildren() {
    return myChildren;
  }

  public boolean isConflicting() {
    return myConflicting;
  }

  public void conflicting() {
    myConflicting = true;
  }

  public boolean isFinite() {
    return myAny || myNothing || mySteps == null || mySteps.isEmpty();
  }

  public boolean isDefinitionNotFound() {
    return myDefinitionNotFound;
  }

  public void definitionNotFound() {
    myDefinitionNotFound = true;
  }

  public void any() {
    myAny = true;
  }

  public void nothing() {
    myNothing = true;
  }

  @NotNull
  public JsonSchemaObject getRoot() {
    return myRoot;
  }

  public Set<JsonSchemaTreeNode> getExcludingChildren() {
    return myExcludingChildren;
  }

  public void setSteps(@Nullable List<JsonSchemaVariantsTreeBuilder.Step> steps) {
    mySteps = steps;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JsonSchemaTreeNode node = (JsonSchemaTreeNode)o;

    if (myAny != node.myAny) return false;
    if (myNothing != node.myNothing) return false;
    if (myParent != null ? !myParent.equals(node.myParent) : node.myParent != null) return false;
    if (!mySchema.equals(node.mySchema)) return false;
    if (mySteps != null ? !mySteps.equals(node.mySteps) : node.mySteps != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myParent != null ? myParent.hashCode() : 0;
    result = 31 * result + (myAny ? 1 : 0);
    result = 31 * result + (myNothing ? 1 : 0);
    result = 31 * result + mySchema.hashCode();
    result = 31 * result + (mySteps != null ? mySteps.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("NODE#" + hashCode() + "\n");
    if (mySteps != null) sb.append(mySteps.stream().map(Object::toString).collect(Collectors.joining("->", "steps: <", ">")));
    else sb.append("steps:<>");
    sb.append("\n");
    if (myAny) sb.append("any");
    else if (myNothing) sb.append("nothing");
    else if (myConflicting) sb.append("conflict");
    else if (myDefinitionNotFound) sb.append("no definition");
    else {
      final String name = mySchema.getSchemaFile() == null ? "null" : mySchema.getSchemaFile().getName();
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
      if (!myExcludingChildren.isEmpty()) {
        sb.append("EXCLUSIVE OR children of NODE#").append(hashCode()).append(":\n----------------\n")
          .append(myExcludingChildren.stream().map(Object::toString).collect(Collectors.joining("\n")))
          .append("\n=================\n");
      }
    }
    return sb.toString();
  }
}
