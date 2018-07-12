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

import com.intellij.json.psi.JsonContainer;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 4/20/2017.
 */
public class JsonSchemaVariantsTreeBuilder {
  private static final Logger LOG = Logger.getInstance(JsonSchemaVariantsTreeBuilder.class);

  public static JsonSchemaTreeNode buildTree(@NotNull final JsonSchemaObject schema,
                                      @Nullable final List<Step> position,
                                      final boolean skipLastExpand,
                                      final boolean literalResolve,
                                      final boolean acceptAdditional) {
    final JsonSchemaTreeNode root = new JsonSchemaTreeNode(null, schema);
    JsonSchemaService service = JsonSchemaService.Impl.get(schema.getJsonObject().getProject());
    expandChildSchema(root, schema, service);
    // set root's position since this children are just variants of root
    for (JsonSchemaTreeNode treeNode : root.getChildren()) {
      treeNode.setSteps(ContainerUtil.notNullize(position));
    }

    final ArrayDeque<JsonSchemaTreeNode> queue = new ArrayDeque<>(root.getChildren());

    while (!queue.isEmpty()) {
      final JsonSchemaTreeNode node = queue.removeFirst();
      if (node.isAny() || node.isNothing() || node.getSteps().isEmpty() || node.getSchema() == null) continue;
      final Step step = node.getSteps().get(0);
      if (!typeMatches(step.isFromObject(), node.getSchema())) {
        node.nothingChild();
        continue;
      }
      if (literalResolve) step.myLiteralResolve = true;
      final Pair<ThreeState, JsonSchemaObject> pair = step.step(node.getSchema(), acceptAdditional);
      if (ThreeState.NO.equals(pair.getFirst())) node.nothingChild();
      else if (ThreeState.YES.equals(pair.getFirst())) node.anyChild();
      else {
        // process step results
        assert pair.getSecond() != null;
        if (node.getSteps().size() > 1 || !skipLastExpand) expandChildSchema(node, pair.getSecond(), service);
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

  private static void expandChildSchema(@NotNull JsonSchemaTreeNode node, @NotNull JsonSchemaObject childSchema, @NotNull JsonSchemaService service) {
    final JsonContainer element = childSchema.getJsonObject();
    if (interestingSchema(childSchema)) {
      final Operation operation =
        CachedValuesManager.getManager(element.getProject())
          .createParameterizedCachedValue((JsonSchemaObject param) -> {
            final Operation expand = new ProcessDefinitionsOperation(param, service);
            expand.doMap(new HashSet<>());
            expand.doReduce();
            return CachedValueProvider.Result.create(expand, element.getContainingFile(),
                                                     service.getAnySchemaChangeTracker());
          }, false).getValue(childSchema);
      node.createChildrenFromOperation(operation);
    }
    else {
      node.setChild(childSchema);
    }
  }

  public static List<Step> buildSteps(@NotNull String nameInSchema) {
    final List<String> chain = StringUtil.split(JsonSchemaService.normalizeId(nameInSchema).replace("\\", "/"), "/");
    List<Step> steps = ContainerUtil.newArrayListWithCapacity(chain.size());
    for (String s: chain) {
      if (!StringUtil.isEmpty(s)) {
        try {
          steps.add(Step.createArrayElementStep(Integer.parseInt(s)));
        }
        catch (NumberFormatException e) {
          steps.add(Step.createPropertyStep(s));
        }
      }
    }
    return steps;
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

    protected abstract void map(@NotNull Set<JsonContainer> visited);
    protected abstract void reduce();

    public void doMap(@NotNull final Set<JsonContainer> visited) {
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
    protected Operation createExpandOperation(@NotNull final JsonSchemaObject schema,
                                              @NotNull JsonSchemaService service) {
      if (conflictingSchema(schema)) {
        final Operation operation = new AnyOfOperation(schema, service);
        operation.myState = SchemaResolveState.conflict;
        return operation;
      }
      if (schema.getAnyOf() != null) return new AnyOfOperation(schema, service);
      if (schema.getOneOf() != null) return new OneOfOperation(schema, service);
      if (schema.getAllOf() != null) return new AllOfOperation(schema, service);
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
    public void map(@NotNull final Set<JsonContainer> visited) {
      JsonSchemaObject current = mySourceNode;
      while (!StringUtil.isEmptyOrSpaces(current.getRef())) {
        final JsonSchemaObject definition = getSchemaFromDefinition(current, myService);
        if (definition == null) {
          myState = SchemaResolveState.brokenDefinition;
          return;
        }
        // this definition was already expanded; do not cycle
        if (!visited.add(definition.getJsonObject())) break;
        current = merge(current, definition, current);
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
    public void map(@NotNull final Set<JsonContainer> visited) {
      assert mySourceNode.getAllOf() != null;
      myChildOperations.addAll(mySourceNode.getAllOf().stream()
                                           .map(sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService)).collect(Collectors.toList()));
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
          ContainerUtil.newArrayListWithCapacity(
            op.myAnyOfGroup.size() * maxSize(myOneOfGroup) +
            myAnyOfGroup.size() * maxSize(op.myOneOfGroup) +
            maxSize(myOneOfGroup) * maxSize(op.myOneOfGroup)
          );

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
    List<JsonSchemaObject> result = ContainerUtil.newArrayListWithCapacity(g1.size() * g2.size());
    for (JsonSchemaObject s: g1) {
      result.addAll(andGroup(s, g2));
    }
    return result;
  }

  // here is important, which pointer gets the result: lets make them all different, otherwise two schemas of branches of oneOf would be equal
  private static List<JsonSchemaObject> andGroup(@NotNull JsonSchemaObject object, @NotNull List<JsonSchemaObject> group) {
    List<JsonSchemaObject> list = ContainerUtil.newArrayListWithCapacity(group.size());
    for (JsonSchemaObject s: group) {
      list.add(merge(object, s, s));
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
    public void map(@NotNull final Set<JsonContainer> visited) {
      assert mySourceNode.getOneOf() != null;
      myChildOperations.addAll(mySourceNode.getOneOf().stream()
                                           .map(sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService)).collect(Collectors.toList()));
    }

    @SuppressWarnings("Duplicates")
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
    public void map(@NotNull final Set<JsonContainer> visited) {
      assert mySourceNode.getAnyOf() != null;
      myChildOperations.addAll(mySourceNode.getAnyOf().stream()
                                           .map(sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService)).collect(Collectors.toList()));
    }

    @SuppressWarnings("Duplicates")
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

  @Nullable
  private static JsonSchemaObject getSchemaFromDefinition(@NotNull final JsonSchemaObject schema,
                                                          @NotNull JsonSchemaService service) {
    final String ref = schema.getRef();
    assert !StringUtil.isEmptyOrSpaces(ref);

    final VirtualFile schemaFile = schema.getSchemaFile();
    final SchemaUrlSplitter splitter = new SchemaUrlSplitter(ref);
    if (splitter.getSchemaId() != null) {
      final VirtualFile refFile = service.findSchemaFileByReference(splitter.getSchemaId(), schemaFile);
      if (refFile == null) {
        LOG.debug(String.format("Schema file not found by reference: '%s' from %s", splitter.getSchemaId(), schemaFile.getPath()));
        return null;
      }
      final JsonSchemaObject refSchema = service.getSchemaObjectForSchemaFile(refFile);
      if (refSchema == null) {
        LOG.debug(String.format("Schema object not found by reference: '%s' from %s", splitter.getSchemaId(), schemaFile.getPath()));
        return null;
      }
      return findRelativeDefinition(refSchema, splitter);
    }
    final JsonSchemaObject rootSchema = service.getSchemaObjectForSchemaFile(schemaFile);
    if (rootSchema == null) {
      LOG.debug(String.format("Schema object not found for %s", schemaFile.getPath()));
      return null;
    }
    return findRelativeDefinition(rootSchema, splitter);
  }

  private static JsonSchemaObject findRelativeDefinition(@NotNull final JsonSchemaObject schema,
                                                         @NotNull final SchemaUrlSplitter splitter) {
    final String path = splitter.getRelativePath();
    if (StringUtil.isEmptyOrSpaces(path)) return schema;
    final JsonSchemaObject definition = schema.findRelativeDefinition(path);
    if (definition == null) {
      LOG.debug(String.format("Definition not found by reference: '%s' in file %s", path, schema.getSchemaFile().getPath()));
    }
    return definition;
  }

  public static JsonSchemaObject merge(@NotNull JsonSchemaObject base,
                                       @NotNull JsonSchemaObject other,
                                       @NotNull JsonSchemaObject pointTo) {
    final JsonSchemaObject object = new JsonSchemaObject(pointTo.getJsonObject());
    object.mergeValues(other);
    object.mergeValues(base);
    object.setRef(other.getRef());
    return object;
  }

  private static boolean conflictingSchema(JsonSchemaObject schema) {
    int cnt = 0;
    if (schema.getAllOf() != null) ++cnt;
    if (schema.getAnyOf() != null) ++cnt;
    if (schema.getOneOf() != null) ++cnt;
    return cnt > 1;
  }

  private static boolean interestingSchema(@NotNull JsonSchemaObject schema) {
    return schema.getAnyOf() != null || schema.getOneOf() != null || schema.getAllOf() != null || schema.getRef() != null
           || schema.getIf() != null;
  }

  public static class Step {
    @Nullable private final String myName;
    private final int myIdx;
    private boolean myLiteralResolve;

    private Step(@Nullable String name, int idx) {
      myName = name;
      myIdx = idx;
    }

    public static Step createPropertyStep(@NotNull final String name) {
      return new Step(name, -1);
    }

    public static Step createArrayElementStep(final int idx) {
      assert idx >= 0;
      return new Step(null, idx);
    }

    public boolean isFromObject() {
      return myName != null;
    }

    public boolean isFromArray() {
      return myName == null;
    }

    @Nullable
    public String getName() {
      return myName;
    }

    @NotNull
    public Pair<ThreeState, JsonSchemaObject> step(@NotNull JsonSchemaObject parent, boolean acceptAdditionalPropertiesSchemas) {
      if (myName != null) {
        return propertyStep(parent, acceptAdditionalPropertiesSchemas);
      } else {
        assert myIdx >= 0;
        return arrayElementStep(parent, acceptAdditionalPropertiesSchemas);
      }
    }

    @Override
    public String toString() {
      String format = "?%s";
      if (myName != null) format = "{%s}";
      if (myIdx >= 0) format = "[%s]";
      return String.format(format, myName != null ? myName : (myIdx >= 0 ? String.valueOf(myIdx) : "null"));
    }

    @NotNull
    private Pair<ThreeState, JsonSchemaObject> propertyStep(@NotNull JsonSchemaObject parent,
                                                            boolean acceptAdditionalPropertiesSchemas) {
      assert myName != null;
      if (JsonSchemaObject.DEFINITIONS.equals(myName) &&
          parent.getDefinitionsMap() != null && (!isInMainSchema(parent) || myLiteralResolve)) {
        // definitions pointer here is fictive so lets find any
        final Map<String, JsonSchemaObject> definitionsMap = parent.getDefinitionsMap();
        final JsonObject anyDefinitions = definitionsMap.values().stream()
          .filter(def -> {
            final JsonProperty parentObj = ObjectUtils.tryCast(def.getJsonObject().getParent(), JsonProperty.class);
            return parentObj != null && parentObj.isValid() && parentObj.getValue() instanceof JsonObject;
          })
          .map(def -> (JsonObject)((JsonProperty) def.getJsonObject().getParent()).getValue())
          .findFirst().orElse(null);
        if (anyDefinitions == null) return Pair.create(ThreeState.NO, null);
        final JsonSchemaObject object = new JsonSchemaObject(anyDefinitions);
        object.setProperties(definitionsMap);
        return Pair.create(ThreeState.UNSURE, object);
      }
      final JsonSchemaObject child = parent.getProperties().get(myName);
      if (child != null) {
        return Pair.create(ThreeState.UNSURE, child);
      }
      final JsonSchemaObject schema = parent.getMatchingPatternPropertySchema(myName);
      if (schema != null) {
        return Pair.create(ThreeState.UNSURE, schema);
      }
      if (acceptAdditionalPropertiesSchemas) {
        if (parent.getAdditionalPropertiesSchema() != null) {
          return Pair.create(ThreeState.UNSURE, parent.getAdditionalPropertiesSchema());
        }

        // resolve inside V7 if-then-else conditionals
        if (parent.getIf() != null) {
          JsonSchemaObject childObject;

          // NOTE: do not resolve inside 'if' itself - it is just a condition, but not an actual validation!
          // only 'then' and 'else' branches provide actual validation sources, but not the 'if' branch

          if (parent.getThen() != null) {
            childObject = parent.getThen().getProperties().get(myName);
            if (childObject != null) {
              return Pair.create(ThreeState.UNSURE, childObject);
            }
          }
          if (parent.getElse() != null) {
            childObject = parent.getElse().getProperties().get(myName);
            if (childObject != null) {
              return Pair.create(ThreeState.UNSURE, childObject);
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

    private static boolean isInMainSchema(@NotNull JsonSchemaObject parent) {
      final VirtualFile schemaFile = parent.getSchemaFile();
      final JsonSchemaService service = JsonSchemaService.Impl.get(parent.getJsonObject().getProject());
      if (!service.isApplicableToFile(schemaFile) || !service.isSchemaFile(schemaFile)) return false;

      final JsonSchemaObject rootSchema = service.getSchemaObjectForSchemaFile(schemaFile);
      if (rootSchema == null) return false;

      return JsonSchemaVersion.isSchemaSchemaId(rootSchema.getId());
    }

    @NotNull
    private Pair<ThreeState, JsonSchemaObject> arrayElementStep(@NotNull JsonSchemaObject parent,
                                                                boolean acceptAdditionalPropertiesSchemas) {
      if (parent.getItemsSchema() != null) {
        return Pair.create(ThreeState.UNSURE, parent.getItemsSchema());
      }
      if (parent.getItemsSchemaList() != null) {
        final List<JsonSchemaObject> list = parent.getItemsSchemaList();
        if (myIdx >= 0 && myIdx < list.size()) {
          return Pair.create(ThreeState.UNSURE, list.get(myIdx));
        }
      }
      if (parent.getAdditionalItemsSchema() != null && acceptAdditionalPropertiesSchemas) {
        return Pair.create(ThreeState.UNSURE, parent.getAdditionalItemsSchema());
      }
      if (Boolean.FALSE.equals(parent.getAdditionalItemsAllowed())) {
        return Pair.create(ThreeState.NO, null);
      }
      return Pair.create(ThreeState.YES, null);
    }
  }

  public static class SchemaUrlSplitter {
    @Nullable
    private final String mySchemaId;
    @NotNull
    private final String myRelativePath;

    public SchemaUrlSplitter(@NotNull final String ref) {
      if ("#".equals(ref)) {
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
