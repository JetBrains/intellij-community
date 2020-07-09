// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.google.common.collect.ImmutableSet;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.JsonPointerUtil.isSelfReference;

/**
 * @author Irina.Chernushina on 4/20/2017.
 */
public class JsonSchemaVariantsTreeBuilder {

  private static final Set<String> SCHEMAS_WITH_TOO_MANY_VARIANTS = ImmutableSet.of(
    "https://github.com/Microsoft/azure-pipelines-vscode/blob/master/local-schema.json"
  );

  public static JsonSchemaTreeNode buildTree(@NotNull Project project,
                                             @NotNull final JsonSchemaObject schema,
                                             @NotNull final JsonPointerPosition position,
                                             final boolean skipLastExpand) {
    final JsonSchemaTreeNode root = new JsonSchemaTreeNode(null, schema);
    if (SCHEMAS_WITH_TOO_MANY_VARIANTS.contains(schema.getId())) {
      return root;
    }
    JsonSchemaService service = JsonSchemaService.Impl.get(project);
    expandChildSchema(root, schema, service);
    // set root's position since this children are just variants of root
    for (JsonSchemaTreeNode treeNode : root.getChildren()) {
      treeNode.setPosition(position);
    }

    final ArrayDeque<JsonSchemaTreeNode> queue = new ArrayDeque<>(root.getChildren());

    while (!queue.isEmpty()) {
      final JsonSchemaTreeNode node = queue.removeFirst();
      if (node.isAny() || node.isNothing() || node.getPosition().isEmpty() || node.getSchema() == null) continue;
      final JsonPointerPosition step = node.getPosition();
      if (!typeMatches(step.isObject(0), node.getSchema())) {
        node.nothingChild();
        continue;
      }
      final Pair<ThreeState, JsonSchemaObject> pair = doSingleStep(step, node.getSchema(), true);
      if (ThreeState.NO.equals(pair.getFirst())) node.nothingChild();
      else if (ThreeState.YES.equals(pair.getFirst())) node.anyChild();
      else {
        // process step results
        assert pair.getSecond() != null;
        if (node.getPosition().size() > 1 || !skipLastExpand) expandChildSchema(node, pair.getSecond(), service);
        else node.setChild(pair.getSecond());
      }

      queue.addAll(node.getChildren());
    }

    return root;
  }

  private static boolean typeMatches(final boolean isObject, @NotNull final JsonSchemaObject schema) {
    final JsonSchemaType requiredType = isObject ? JsonSchemaType._object : JsonSchemaType._array;
    if (schema.getType() != null) {
      return requiredType.equals(schema.getType());
    }
    if (schema.getTypeVariants() != null) {
      for (JsonSchemaType schemaType : schema.getTypeVariants()) {
        if (requiredType.equals(schemaType)) return true;
      }
      return false;
    }
    return true;
  }

  private static void expandChildSchema(@NotNull JsonSchemaTreeNode node,
                                        @NotNull JsonSchemaObject childSchema,
                                        @NotNull JsonSchemaService service) {
    if (interestingSchema(childSchema)) {
      node.createChildrenFromOperation(getOperation(service, childSchema));
    }
    else {
      node.setChild(childSchema);
    }
  }

  @NotNull
  private static Operation getOperation(@NotNull JsonSchemaService service,
                                        JsonSchemaObject param) {
    final Operation expand = new ProcessDefinitionsOperation(param, service);
    expand.doMap(new HashSet<>());
    expand.doReduce();
    return expand;
  }

  @NotNull
  public static Pair<ThreeState, JsonSchemaObject> doSingleStep(@NotNull JsonPointerPosition step,
                                                                @NotNull JsonSchemaObject parent,
                                                                boolean processAllBranches) {
    final String name = step.getFirstName();
    if (name != null) {
      return propertyStep(name, parent, processAllBranches);
    } else {
      final int index = step.getFirstIndex();
      assert index >= 0;
      return arrayOrNumericPropertyElementStep(index, parent);
    }
  }

  static abstract class Operation {
    @NotNull final List<JsonSchemaObject> myAnyOfGroup = new SmartList<>();
    @NotNull final List<List<JsonSchemaObject>> myOneOfGroup = new SmartList<>();
    @NotNull protected final List<Operation> myChildOperations;
    @NotNull protected final JsonSchemaObject mySourceNode;
    protected SchemaResolveState myState = SchemaResolveState.normal;

    protected Operation(@NotNull JsonSchemaObject sourceNode) {
      mySourceNode = sourceNode;
      myChildOperations = new ArrayList<>();
    }

    protected abstract void map(@NotNull Set<JsonSchemaObject> visited);
    protected abstract void reduce();

    public void doMap(@NotNull final Set<JsonSchemaObject> visited) {
      map(visited);
      for (Operation operation : myChildOperations) {
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
        myChildOperation.doReduce();
      }
      reduce();
      myChildOperations.clear();
    }

    private static void clearVariants(@NotNull JsonSchemaObject object) {
      object.setAllOf(null);
      object.setAnyOf(null);
      object.setOneOf(null);
    }

    @Nullable
    protected Operation createExpandOperation(@NotNull JsonSchemaObject schema,
                                              @NotNull JsonSchemaService service) {
      Operation forConflict = getOperationForConflict(schema, service);
      if (forConflict != null) return forConflict;
      if (schema.getAnyOf() != null) return new AnyOfOperation(schema, service);
      if (schema.getOneOf() != null) return new OneOfOperation(schema, service);
      if (schema.getAllOf() != null) return new AllOfOperation(schema, service);
      return null;
    }

    @Nullable
    private static Operation getOperationForConflict(@NotNull JsonSchemaObject schema,
                                                     @NotNull JsonSchemaService service) {
      // in case of several incompatible operations, choose the most permissive one
      List<JsonSchemaObject> anyOf = schema.getAnyOf();
      List<JsonSchemaObject> oneOf = schema.getOneOf();
      List<JsonSchemaObject> allOf = schema.getAllOf();
      if (anyOf != null && (oneOf != null || allOf != null)) {
        return new AnyOfOperation(schema, service) {{myState = SchemaResolveState.conflict;}};
      }
      else if (oneOf != null && allOf != null) {
        return new OneOfOperation(schema, service) {{myState = SchemaResolveState.conflict;}};
      }
      return null;
    }

    protected static List<JsonSchemaObject> mergeOneOf(Operation op) {
      return op.myOneOfGroup.stream().flatMap(List::stream).collect(Collectors.toList());
    }
  }

  // even if there are no definitions to expand, this object may work as an intermediate node in a tree,
  // connecting oneOf and allOf expansion, for example
  private static class ProcessDefinitionsOperation extends Operation {
    private final JsonSchemaService myService;

    protected ProcessDefinitionsOperation(@NotNull JsonSchemaObject sourceNode, JsonSchemaService service) {
      super(sourceNode);
      myService = service;
    }

    @Override
    public void map(@NotNull final Set<JsonSchemaObject> visited) {
      JsonSchemaObject current = mySourceNode;
      while (!StringUtil.isEmptyOrSpaces(current.getRef())) {
        final JsonSchemaObject definition = current.resolveRefSchema(myService);
        if (definition == null) {
          myState = SchemaResolveState.brokenDefinition;
          return;
        }
        // this definition was already expanded; do not cycle
        if (!visited.add(definition)) break;
        current = JsonSchemaObject.merge(current, definition, current);
      }
      final Operation expandOperation = createExpandOperation(current, myService);
      if (expandOperation != null) myChildOperations.add(expandOperation);
      else myAnyOfGroup.add(current);
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

  private static class AllOfOperation extends Operation {
    private final JsonSchemaService myService;

    protected AllOfOperation(@NotNull JsonSchemaObject sourceNode, JsonSchemaService service) {
      super(sourceNode);
      myService = service;
    }

    @Override
    public void map(@NotNull final Set<JsonSchemaObject> visited) {
      List<JsonSchemaObject> allOf = mySourceNode.getAllOf();
      assert allOf != null;
      myChildOperations.addAll(ContainerUtil.map(allOf, sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService)));
    }

    private static <T> int maxSize(List<List<T>> items) {
      if (items.size() == 0) return 0;
      int maxsize = -1;
      for (List<T> item: items) {
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

        final List<JsonSchemaObject> mergedAny = andGroups(op.myAnyOfGroup, myAnyOfGroup);

        final List<List<JsonSchemaObject>> mergedExclusive =
          new ArrayList<>(op.myAnyOfGroup.size() * maxSize(myOneOfGroup) +
                          myAnyOfGroup.size() * maxSize(op.myOneOfGroup) +
                          maxSize(myOneOfGroup) * maxSize(op.myOneOfGroup));

        for (List<JsonSchemaObject> objects : myOneOfGroup) {
          mergedExclusive.add(andGroups(op.myAnyOfGroup, objects));
        }
        for (List<JsonSchemaObject> objects : op.myOneOfGroup) {
          mergedExclusive.add(andGroups(objects, myAnyOfGroup));
        }
        for (List<JsonSchemaObject> group : op.myOneOfGroup) {
          for (List<JsonSchemaObject> otherGroup : myOneOfGroup) {
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

  private static List<JsonSchemaObject> andGroups(@NotNull List<JsonSchemaObject> g1,
                                                  @NotNull List<JsonSchemaObject> g2) {
    List<JsonSchemaObject> result = new ArrayList<>(g1.size() * g2.size());
    for (JsonSchemaObject s: g1) {
      result.addAll(andGroup(s, g2));
    }
    return result;
  }

  // here is important, which pointer gets the result: lets make them all different, otherwise two schemas of branches of oneOf would be equal
  private static List<JsonSchemaObject> andGroup(@NotNull JsonSchemaObject object, @NotNull List<JsonSchemaObject> group) {
    List<JsonSchemaObject> list = new ArrayList<>(group.size());
    for (JsonSchemaObject s: group) {
      JsonSchemaObject schemaObject = JsonSchemaObject.merge(object, s, s);
      if (schemaObject.isValidByExclusion()) {
        list.add(schemaObject);
      }
    }
    return list;
  }

  private static class OneOfOperation extends Operation {
    private final JsonSchemaService myService;

    protected OneOfOperation(@NotNull JsonSchemaObject sourceNode, JsonSchemaService service) {
      super(sourceNode);
      myService = service;
    }

    @Override
    public void map(@NotNull final Set<JsonSchemaObject> visited) {
      List<JsonSchemaObject> oneOf = mySourceNode.getOneOf();
      assert oneOf != null;
      myChildOperations.addAll(ContainerUtil.map(oneOf, sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService)));
    }

    @Override
    public void reduce() {
      final List<JsonSchemaObject> oneOf = new SmartList<>();
      for (Operation op : myChildOperations) {
        if (!op.myState.equals(SchemaResolveState.normal)) continue;
        oneOf.addAll(andGroup(mySourceNode, op.myAnyOfGroup));
        oneOf.addAll(andGroup(mySourceNode, mergeOneOf(op)));
      }
      // here it is not a mistake - all children of this node come to oneOf group
      myOneOfGroup.add(oneOf);
    }
  }

  private static class AnyOfOperation extends Operation {
    private final JsonSchemaService myService;

    protected AnyOfOperation(@NotNull JsonSchemaObject sourceNode, JsonSchemaService service) {
      super(sourceNode);
      myService = service;
    }

    @Override
    public void map(@NotNull final Set<JsonSchemaObject> visited) {
      List<JsonSchemaObject> anyOf = mySourceNode.getAnyOf();
      assert anyOf != null;
      myChildOperations.addAll(ContainerUtil.map(anyOf, sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService)));
    }

    @Override
    public void reduce() {
      for (Operation op : myChildOperations) {
        if (!op.myState.equals(SchemaResolveState.normal)) continue;

        myAnyOfGroup.addAll(andGroup(mySourceNode, op.myAnyOfGroup));
        for (List<JsonSchemaObject> group : op.myOneOfGroup) {
          myOneOfGroup.add(andGroup(mySourceNode, group));
        }
      }
    }
  }

  private static boolean interestingSchema(@NotNull JsonSchemaObject schema) {
    return schema.getAnyOf() != null || schema.getOneOf() != null || schema.getAllOf() != null || schema.getRef() != null
           || schema.getIfThenElse() != null;
  }


  @NotNull
  private static Pair<ThreeState, JsonSchemaObject> propertyStep(@NotNull String name,
                                                                 @NotNull JsonSchemaObject parent,
                                                                 boolean processAllBranches) {
    final JsonSchemaObject child = parent.getProperties().get(name);
    if (child != null) {
      return Pair.create(ThreeState.UNSURE, child);
    }
    final JsonSchemaObject schema = parent.getMatchingPatternPropertySchema(name);
    if (schema != null) {
      return Pair.create(ThreeState.UNSURE, schema);
    }
    if (parent.getAdditionalPropertiesSchema() != null) {
      return Pair.create(ThreeState.UNSURE, parent.getAdditionalPropertiesSchema());
    }

    if (processAllBranches) {
      List<IfThenElse> ifThenElseList = parent.getIfThenElse();
      if (ifThenElseList != null) {
        for (IfThenElse ifThenElse : ifThenElseList) {
          // resolve inside V7 if-then-else conditionals
          JsonSchemaObject childObject;

          // NOTE: do not resolve inside 'if' itself - it is just a condition, but not an actual validation!
          // only 'then' and 'else' branches provide actual validation sources, but not the 'if' branch

          JsonSchemaObject then = ifThenElse.getThen();
          //noinspection Duplicates
          if (then != null) {
            childObject = then.getProperties().get(name);
            if (childObject != null) {
              return Pair.create(ThreeState.UNSURE, childObject);
            }
          }
          JsonSchemaObject elseBranch = ifThenElse.getElse();
          //noinspection Duplicates
          if (elseBranch != null) {
            childObject = elseBranch.getProperties().get(name);
            if (childObject != null) {
              return Pair.create(ThreeState.UNSURE, childObject);
            }
          }
        }
      }
    }
    if (Boolean.FALSE.equals(parent.getAdditionalPropertiesAllowed())) {
      return Pair.create(ThreeState.NO, null);
    }
    // by default, additional properties are allowed
    return Pair.create(ThreeState.YES, null);
  }

  @NotNull
  private static Pair<ThreeState, JsonSchemaObject> arrayOrNumericPropertyElementStep(int idx, @NotNull JsonSchemaObject parent) {
    if (parent.getItemsSchema() != null) {
      return Pair.create(ThreeState.UNSURE, parent.getItemsSchema());
    }
    if (parent.getItemsSchemaList() != null) {
      final List<JsonSchemaObject> list = parent.getItemsSchemaList();
      if (idx >= 0 && idx < list.size()) {
        return Pair.create(ThreeState.UNSURE, list.get(idx));
      }
    }
    final String keyAsString = String.valueOf(idx);
    if (parent.getProperties().containsKey(keyAsString)) {
      return Pair.create(ThreeState.UNSURE, parent.getProperties().get(keyAsString));
    }
    final JsonSchemaObject matchingPatternPropertySchema = parent.getMatchingPatternPropertySchema(keyAsString);
    if (matchingPatternPropertySchema != null) {
      return Pair.create(ThreeState.UNSURE, matchingPatternPropertySchema);
    }
    if (parent.getAdditionalItemsSchema() != null) {
      return Pair.create(ThreeState.UNSURE, parent.getAdditionalItemsSchema());
    }
    if (Boolean.FALSE.equals(parent.getAdditionalItemsAllowed())) {
      return Pair.create(ThreeState.NO, null);
    }
    return Pair.create(ThreeState.YES, null);
  }

  public static class SchemaUrlSplitter {
    @Nullable
    private final String mySchemaId;
    @NotNull
    private final String myRelativePath;

    public SchemaUrlSplitter(@NotNull final String ref) {
      if (isSelfReference(ref)) {
        mySchemaId = null;
        myRelativePath = "";
        return;
      }
      if (!ref.startsWith("#/")) {
        int idx = ref.indexOf("#/");
        if (idx == -1) {
          mySchemaId = ref.endsWith("#") ? ref.substring(0, ref.length() - 1) : ref;
          myRelativePath = "";
        } else {
          mySchemaId = ref.substring(0, idx);
          myRelativePath = ref.substring(idx);
        }
      } else {
        mySchemaId = null;
        myRelativePath = ref;
      }
    }

    public boolean isAbsolute() {
      return mySchemaId != null;
    }

    @Nullable
    public String getSchemaId() {
      return mySchemaId;
    }

    @NotNull
    public String getRelativePath() {
      return myRelativePath;
    }
  }
}
