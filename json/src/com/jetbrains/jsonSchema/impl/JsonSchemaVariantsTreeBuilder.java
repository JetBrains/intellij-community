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

import com.intellij.json.psi.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SmartPsiElementPointer;
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
  @NotNull private final JsonSchemaObject myRootSchema;
  @NotNull private final JsonSchemaObject mySchema;
  private final boolean myIsName;
  @NotNull private final List<Step> myPosition;

  public JsonSchemaVariantsTreeBuilder(@NotNull final JsonSchemaObject rootSchema,
                                       @NotNull final JsonSchemaObject schema,
                                       final boolean isName,
                                       @Nullable final List<Step> position) {
    myRootSchema = rootSchema;
    mySchema = schema;
    myIsName = isName;
    myPosition = ContainerUtil.notNullize(position);
  }

  public JsonSchemaTreeNode buildTree() {
    final Map<VirtualFile, JsonSchemaObject> rootSchemasMap = new HashMap<>();
    rootSchemasMap.put(myRootSchema.getSchemaFile(), myRootSchema);

    final JsonSchemaTreeNode root = new JsonSchemaTreeNode(null, mySchema);
    applyChildSchema(rootSchemasMap, root, mySchema);
    // set root's position since this children are just variants of root
    root.getChildren().forEach(node -> node.setSteps(myPosition));

    final ArrayDeque<JsonSchemaTreeNode> queue = new ArrayDeque<>();
    queue.addAll(root.getChildren());

    while (!queue.isEmpty()) {
      final JsonSchemaTreeNode node = queue.removeFirst();
      if (node.isAny() || node.isNothing() || node.getSteps().isEmpty() || node.getSchema() == null) continue;
      final Step step = node.getSteps().get(0);
      if (!typeMatches(step.isFromObject(), node.getSchema())) {
        continue;
      }
      final Pair<ThreeState, JsonSchemaObject> pair = step.step(node.getSchema(), !myIsName);
      if (ThreeState.NO.equals(pair.getFirst())) node.nothingChild();
      else if (ThreeState.YES.equals(pair.getFirst())) node.anyChild();
      else {
        // process step results
        assert pair.getSecond() != null;
        applyChildSchema(rootSchemasMap, node, pair.getSecond());
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

  private static void applyChildSchema(@NotNull Map<VirtualFile, JsonSchemaObject> rootSchemasMap,
                                       @NotNull JsonSchemaTreeNode node,
                                       @NotNull JsonSchemaObject childSchema) {
    if (interestingSchema(childSchema)) {
      final ProcessDefinitionsOperation operation = new ProcessDefinitionsOperation(childSchema, rootSchemasMap);
      operation.doMap();
      operation.doReduce();
      node.createChildrenFromOperation(operation);
    } else {
      node.setChild(childSchema);
    }
  }

  public static List<Step> buildSteps(@NotNull String nameInSchema) {
    final List<String> chain = StringUtil.split(JsonSchemaService.normalizeId(nameInSchema).replace("\\", "/"), "/");
    return chain.stream().filter(s -> !s.isEmpty())
      .map(item -> Step.createPropertyStep(item))
      .collect(Collectors.toList());
  }

  static abstract class Operation {
    @NotNull final List<JsonSchemaObject> myAnyOfGroup = new SmartList<>();
    @NotNull final List<JsonSchemaObject> myOneOfGroup = new SmartList<>();
    @NotNull protected final List<Operation> myChildOperations;
    @NotNull protected final JsonSchemaObject mySourceNode;
    protected SchemaResolveState myState = SchemaResolveState.normal;
    @NotNull protected final Map<VirtualFile, JsonSchemaObject> myRootSchemasMap;

    protected Operation(@NotNull JsonSchemaObject sourceNode,
                        @NotNull Map<VirtualFile, JsonSchemaObject> map) {
      mySourceNode = sourceNode;
      myRootSchemasMap = map;
      myChildOperations = new ArrayList<>();
    }

    protected abstract void map();
    protected abstract void reduce();

    public void doMap() {
      map();
      myChildOperations.forEach(Operation::doMap);
    }

    public void doReduce() {
      if (!SchemaResolveState.normal.equals(myState)) {
        myChildOperations.clear();
        myAnyOfGroup.clear();
        myOneOfGroup.clear();
        return;
      }
      myChildOperations.forEach(Operation::doReduce);
      reduce();
    }

    @Nullable
    protected Operation createExpandOperation(@NotNull final JsonSchemaObject schema) {
      if (conflictingSchema(schema)) {
        final Operation operation = new AnyOfOperation(schema, myRootSchemasMap);
        operation.myState = SchemaResolveState.conflict;
        return operation;
      }
      if (schema.getAnyOf() != null) return new AnyOfOperation(schema, myRootSchemasMap);
      if (schema.getOneOf() != null) return new OneOfOperation(schema, myRootSchemasMap);
      if (schema.getAllOf() != null) return new AllOfOperation(schema, myRootSchemasMap);
      return null;
    }
  }

  // even if there are no definitions to expand, this object may work as an intermediate node in a tree,
  // connecting oneOf and allOf expansion, for example
  private static class ProcessDefinitionsOperation extends Operation {
    protected ProcessDefinitionsOperation(@NotNull JsonSchemaObject sourceNode,
                                          @NotNull Map<VirtualFile, JsonSchemaObject> map) {
      super(sourceNode, map);
    }

    @Override
    public void map() {
      final Set<Pair<VirtualFile, String>> control = new HashSet<>();
      JsonSchemaObject current = mySourceNode;
      while (!StringUtil.isEmptyOrSpaces(current.getRef())) {
        if (!control.add(Pair.create(current.getSchemaFile(), current.getRef()))) {
          myState = SchemaResolveState.cyclicDefinition;
          LOG.debug(String.format("Cyclic definition: '%s' in file %s", current.getRef(), current.getSchemaFile()));
          return;
        }
        final JsonSchemaObject definition = getSchemaFromDefinition(current, myRootSchemasMap);
        if (definition == null) {
          myState = SchemaResolveState.brokenDefinition;
          return;
        }
        current = merge(current, definition, current);
      }
      final Operation expandOperation = createExpandOperation(current);
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
    protected AllOfOperation(@NotNull JsonSchemaObject sourceNode,
                             @NotNull Map<VirtualFile, JsonSchemaObject> map) {
      super(sourceNode, map);
    }

    @Override
    public void map() {
      assert mySourceNode.getAllOf() != null;
      myChildOperations.addAll(mySourceNode.getAllOf().stream()
        .map(s -> new ProcessDefinitionsOperation(s, myRootSchemasMap)).collect(Collectors.toList()));
    }

    @Override
    public void reduce() {
      myAnyOfGroup.add(mySourceNode);

      myChildOperations.forEach(op -> {
        if (!op.myState.equals(SchemaResolveState.normal)) return;

        final List<JsonSchemaObject> mergedAny = andGroups(op.myAnyOfGroup, myAnyOfGroup);

        final List<JsonSchemaObject> mergedExclusive = andGroups(op.myAnyOfGroup, myOneOfGroup);
        mergedExclusive.addAll(andGroups(op.myOneOfGroup, myAnyOfGroup));
        mergedExclusive.addAll(andGroups(op.myOneOfGroup, myOneOfGroup));

        myAnyOfGroup.clear();
        myOneOfGroup.clear();
        myAnyOfGroup.addAll(mergedAny);
        myOneOfGroup.addAll(mergedExclusive);
      });
    }
  }

  private static List<JsonSchemaObject> andGroups(@NotNull List<JsonSchemaObject> g1,
                                                  @NotNull List<JsonSchemaObject> g2) {
    return g1.stream().map(s -> andGroup(s, g2)).flatMap(List::stream).collect(Collectors.toList());
  }

  // here is important, which pointer gets the result: lets make them all different, otherwise two schemas of branches of oneOf would be equal
  private static List<JsonSchemaObject> andGroup(@NotNull JsonSchemaObject object, @NotNull List<JsonSchemaObject> group) {
    return group.stream().map(s -> merge(object, s, s)).collect(Collectors.toList());
  }

  private static class OneOfOperation extends Operation {
    protected OneOfOperation(@NotNull JsonSchemaObject sourceNode,
                             @NotNull Map<VirtualFile, JsonSchemaObject> map) {
      super(sourceNode, map);
    }

    @Override
    public void map() {
      assert mySourceNode.getOneOf() != null;
      myChildOperations.addAll(mySourceNode.getOneOf().stream()
                                 .map(s -> new ProcessDefinitionsOperation(s, myRootSchemasMap)).collect(Collectors.toList()));
    }

    @Override
    public void reduce() {
      myChildOperations.forEach(op -> {
        if (!op.myState.equals(SchemaResolveState.normal)) return;
        // here it is not a mistake - all children of this node come to
        myOneOfGroup.addAll(andGroup(mySourceNode, op.myAnyOfGroup));
        myOneOfGroup.addAll(andGroup(mySourceNode, op.myOneOfGroup));
      });
    }
  }

  private static class AnyOfOperation extends Operation {
    protected AnyOfOperation(@NotNull JsonSchemaObject sourceNode,
                             @NotNull Map<VirtualFile, JsonSchemaObject> map) {
      super(sourceNode, map);
    }

    @Override
    public void map() {
      assert mySourceNode.getAnyOf() != null;
      myChildOperations.addAll(mySourceNode.getAnyOf().stream()
                                 .map(s -> new ProcessDefinitionsOperation(s, myRootSchemasMap)).collect(Collectors.toList()));
    }

    @Override
    public void reduce() {
      myChildOperations.forEach(op -> {
        if (!op.myState.equals(SchemaResolveState.normal)) return;

        myAnyOfGroup.addAll(andGroup(mySourceNode, op.myAnyOfGroup));
        myOneOfGroup.addAll(andGroup(mySourceNode, op.myOneOfGroup));
      });
    }
  }

  // todo maybe it is also a separate class
  @Nullable
  private static JsonSchemaObject getSchemaFromDefinition(@NotNull final JsonSchemaObject schema,
                                                     @NotNull Map<VirtualFile, JsonSchemaObject> rootSchemasMap) {
    final String ref = schema.getRef();
    assert !StringUtil.isEmptyOrSpaces(ref);

    final VirtualFile schemaFile = schema.getSchemaFile();
    if (schemaFile == null) {
      LOG.debug("No schema file for schema");
      return null;
    }

    final JsonSchemaReader.SchemaUrlSplitter splitter = new JsonSchemaReader.SchemaUrlSplitter(ref);
    if (splitter.getSchemaId() != null) {
      final JsonSchemaService service = JsonSchemaService.Impl.get(schema.getPeerPointer().getProject());
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
      rootSchemasMap.put(refFile, refSchema);
      return findRelativeDefinition(refSchema, splitter);
    }
    final JsonSchemaObject rootSchema = rootSchemasMap.get(schemaFile);
    if (rootSchema == null) {
      LOG.debug(String.format("Schema object not found for %s", schemaFile.getPath()));
      return null;
    }
    return findRelativeDefinition(rootSchema, splitter);
  }

  private static JsonSchemaObject findRelativeDefinition(@NotNull final JsonSchemaObject schema,
                                                         @NotNull final JsonSchemaReader.SchemaUrlSplitter splitter) {
    final String path = splitter.getRelativePath();
    if (StringUtil.isEmptyOrSpaces(path)) return schema;
    final JsonSchemaObject definition = schema.findRelativeDefinition(path);
    if (definition == null) {
      LOG.debug(String.format("Definition not found by reference: '%s' in file %s", path,
                              schema.getSchemaFile() == null ? "" : schema.getSchemaFile().getPath()));
    }
    return definition;
  }

  // todo do not forget to create caches for calculated merges etc etc
  public static JsonSchemaObject merge(@NotNull JsonSchemaObject base,
                                       @NotNull JsonSchemaObject other,
                                       @NotNull JsonSchemaObject pointTo) {
    final JsonSchemaObject object = new JsonSchemaObject(pointTo.getPeerPointer());
    object.mergeValues(other);
    object.mergeValues(base);
    object.setRef(other.getRef());
    object.setDefinitionsPointer(base.getDefinitionsPointer());
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
    return schema.getAnyOf() != null || schema.getOneOf() != null || schema.getAllOf() != null || schema.getRef() != null;
  }

  public static class Step {
    @Nullable private final String myName;
    private final int myIdx;

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
      if (JsonSchemaObject.DEFINITIONS.equals(myName) && parent.getDefinitions() != null) {
        final SmartPsiElementPointer<JsonObject> pointer = parent.getDefinitionsPointer();
        final JsonSchemaObject object = new JsonSchemaObject(pointer);
        object.setProperties(parent.getDefinitions());
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
      if (parent.getAdditionalPropertiesSchema() != null && acceptAdditionalPropertiesSchemas) {
        return Pair.create(ThreeState.UNSURE, parent.getAdditionalPropertiesSchema());
      }
      if (Boolean.FALSE.equals(parent.getAdditionalPropertiesAllowed())) {
        return Pair.create(ThreeState.NO, null);
      }
      // by default, additional properties are allowed
      return Pair.create(ThreeState.YES, null);
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
}
