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
      if (node.isFinite()) continue;
      final List<Step> steps = node.getSteps();
      //noinspection ConstantConditions
      final Step step = steps.get(0);
      if (step.getTransition() == null || !byStateType(step.getType(), node.getSchema())) {
        continue;
      }
      // todo further get rid of consumer?
      final TransitionResultConsumer consumer = new TransitionResultConsumer();
      step.getTransition().step(node.getSchema(), consumer, !myIsName);
      if (consumer.isNothing()) node.setChild(JsonSchemaTreeNode.createNothing(node));
      else if (consumer.isAny()) node.setChild(JsonSchemaTreeNode.createAny(node));
      else {
        // process step results
        assert consumer.getSchema() != null;
        applyChildSchema(rootSchemasMap, node, consumer.getSchema());
      }

      queue.addAll(node.getChildren());
      queue.addAll(node.getExcludingChildren());
    }

    return root;
  }

  private static boolean byStateType(@NotNull final StateType type, @NotNull final JsonSchemaObject schema) {
    if (StateType._unknown.equals(type)) return true;
    final JsonSchemaType requiredType = type.getCorrespondingJsonType();
    if (requiredType == null) return true;
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
      // todo consider standard tree walker
      final ProcessDefinitionsOperation operation = new ProcessDefinitionsOperation(childSchema, rootSchemasMap);
      operation.doMap();
      operation.doReduce();
      if (StepState.brokenDefinition.equals(operation.myState) || StepState.cyclicDefinition.equals(operation.myState))
        node.setChild(JsonSchemaTreeNode.createNoDefinition(node));
      else {
        node.addChildren(operation.myReduceContext.myAnyGroup.stream()
                           .map(s -> new JsonSchemaTreeNode(node, s)).collect(Collectors.toList()));
        node.addExcludingChildren(operation.myReduceContext.myExclusiveOrGroup.stream()
                           .map(s -> new JsonSchemaTreeNode(node, s)).collect(Collectors.toList()));
      }
    } else {
      if (conflictingSchema(childSchema)) {
        node.setChild(JsonSchemaTreeNode.createConflicting(node));
      } else node.setChild(new JsonSchemaTreeNode(node, childSchema));
    }
  }

  public static Pair<List<Step>, String> buildSteps(@NotNull String nameInSchema) {
    final List<String> chain = StringUtil.split(JsonSchemaService.normalizeId(nameInSchema).replace("\\", "/"), "/");
    final List<Step> steps = chain.stream().filter(s -> !s.isEmpty()).map(item -> new Step(StateType._unknown, new PropertyTransition(item)))
      .collect(Collectors.toList());
    if (steps.isEmpty()) return Pair.create(Collections.emptyList(), nameInSchema);
    return Pair.create(steps, chain.get(chain.size() - 1));
  }

  static enum StepState {
    normal, conflict, brokenDefinition, cyclicDefinition;
  }

  public enum StateType {
    _object(JsonSchemaType._object), _array(JsonSchemaType._array), _value(null), _unknown(null);

    @Nullable
    private final JsonSchemaType myCorrespondingJsonType;

    StateType(@Nullable JsonSchemaType correspondingJsonType) {
      myCorrespondingJsonType = correspondingJsonType;
    }

    @Nullable
    public JsonSchemaType getCorrespondingJsonType() {
      return myCorrespondingJsonType;
    }
  }

  public interface Transition {

    void step(@NotNull JsonSchemaObject parent,
              @NotNull TransitionResultConsumerI resultConsumer,
              boolean acceptAdditionalPropertiesSchemas);
  }

  private static abstract class Operation {
    // todo theoretically, we could use just pairs here
    @NotNull protected final ReduceContext myReduceContext;
    @NotNull protected final List<Operation> myChildOperations;
    @NotNull protected final JsonSchemaObject mySourceNode;
    protected StepState myState = StepState.normal;
    @NotNull protected final Map<VirtualFile, JsonSchemaObject> myRootSchemasMap;

    protected Operation(@NotNull JsonSchemaObject sourceNode,
                        @NotNull Map<VirtualFile, JsonSchemaObject> map) {
      mySourceNode = sourceNode;
      myRootSchemasMap = map;
      myReduceContext = new ReduceContext();
      myChildOperations = new ArrayList<>();
    }

    protected abstract void map();
    protected abstract void reduce();

    public void doMap() {
      map();
      myChildOperations.forEach(Operation::doMap);
    }

    public void doReduce() {
      if (!StepState.normal.equals(myState)) {
        myChildOperations.clear();
        myReduceContext.clear();
        return;
      }
      myChildOperations.forEach(Operation::doReduce);
      reduce();
    }

    @Nullable
    protected Operation createExpandOperation(@NotNull final JsonSchemaObject schema) {
      if (conflictingSchema(schema)) {
        final Operation operation = new AnyOfOperation(schema, myRootSchemasMap);
        operation.myState = StepState.conflict;
        return operation;
      }
      if (schema.getAnyOf() != null) return new AnyOfOperation(schema, myRootSchemasMap);
      if (schema.getOneOf() != null) return new OneOfOperation(schema, myRootSchemasMap);
      if (schema.getAllOf() != null) return new AllOfOperation(schema, myRootSchemasMap);
      return null;
    }
  }

  // todo maybe this will be removed
  private static class ReduceContext {
    @NotNull final List<JsonSchemaObject> myAnyGroup = new ArrayList<>();
    @NotNull final List<JsonSchemaObject> myExclusiveOrGroup = new ArrayList<>();

    public void clear() {
      myExclusiveOrGroup.clear();
      myAnyGroup.clear();
    }

    public ReduceContext copy() {
      final ReduceContext context = new ReduceContext();
      context.myAnyGroup.addAll(myAnyGroup);
      context.myExclusiveOrGroup.addAll(myExclusiveOrGroup);
      return context;
    }
  }

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
          myState = StepState.cyclicDefinition;
          LOG.debug(String.format("Cyclic definition: '%s' in file %s", current.getRef(), current.getSchemaFile()));
          return;
        }
        final JsonSchemaObject definition = getSchemaFromDefinition(current, myRootSchemasMap);
        if (definition == null) {
          myState = StepState.brokenDefinition;
          return;
        }
        current = merge(current, definition);
      }
      final Operation expandOperation = createExpandOperation(current);
      if (expandOperation != null) myChildOperations.add(expandOperation);
      else myReduceContext.myAnyGroup.add(current);
    }

    @Override
    public void reduce() {
      if (!myChildOperations.isEmpty()) {
        assert myChildOperations.size() == 1;
        myReduceContext.myAnyGroup.addAll(myChildOperations.get(0).myReduceContext.myAnyGroup);
        myReduceContext.myExclusiveOrGroup.addAll(myChildOperations.get(0).myReduceContext.myExclusiveOrGroup);
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
      myReduceContext.myAnyGroup.add(mySourceNode);

      myChildOperations.forEach(op -> {
        if (!op.myState.equals(StepState.normal)) return;

        final List<JsonSchemaObject> mergedAny = andGroups(op.myReduceContext.myAnyGroup, myReduceContext.myAnyGroup);

        final List<JsonSchemaObject> mergedExclusive = andGroups(op.myReduceContext.myAnyGroup, myReduceContext.myExclusiveOrGroup);
        mergedExclusive.addAll(andGroups(op.myReduceContext.myExclusiveOrGroup, myReduceContext.myAnyGroup));
        mergedExclusive.addAll(andGroups(op.myReduceContext.myExclusiveOrGroup, myReduceContext.myExclusiveOrGroup));

        myReduceContext.clear();
        myReduceContext.myAnyGroup.addAll(mergedAny);
        myReduceContext.myExclusiveOrGroup.addAll(mergedExclusive);
      });
    }
  }

  private static List<JsonSchemaObject> andGroups(@NotNull List<JsonSchemaObject> g1,
                                                  @NotNull List<JsonSchemaObject> g2) {
    return g1.stream().map(s -> andGroup(s, g2)).flatMap(List::stream).collect(Collectors.toList());
  }

  private static List<JsonSchemaObject> andGroup(@NotNull JsonSchemaObject object, @NotNull List<JsonSchemaObject> group) {
    return group.stream().map(s -> merge(object, s)).collect(Collectors.toList());
  }

  private static class OneOfOperation extends Operation {
    protected OneOfOperation(@NotNull JsonSchemaObject sourceNode,
                             @NotNull Map<VirtualFile, JsonSchemaObject> map) {
      super(sourceNode, map);
    }

    @Override
    public void map() {
      // todo creation of process definition operation should be done only if needed -> we need checks
      assert mySourceNode.getOneOf() != null;
      myChildOperations.addAll(mySourceNode.getOneOf().stream()
                                 .map(s -> new ProcessDefinitionsOperation(s, myRootSchemasMap)).collect(Collectors.toList()));
    }

    @Override
    public void reduce() {
      myChildOperations.forEach(op -> {
        if (!op.myState.equals(StepState.normal)) return;

        myReduceContext.myExclusiveOrGroup.addAll(andGroup(mySourceNode, op.myReduceContext.myAnyGroup));
        myReduceContext.myExclusiveOrGroup.addAll(andGroup(mySourceNode, op.myReduceContext.myExclusiveOrGroup));
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
        if (!op.myState.equals(StepState.normal)) return;

        myReduceContext.myAnyGroup.addAll(op.myReduceContext.myAnyGroup);
        myReduceContext.myExclusiveOrGroup.addAll(op.myReduceContext.myExclusiveOrGroup);
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
  public static JsonSchemaObject merge(@NotNull JsonSchemaObject base, @NotNull JsonSchemaObject other) {
    final JsonSchemaObject object = new JsonSchemaObject(base.getPeerPointer());
    object.mergeValues(other);
    object.mergeValues(base);
    object.setRef(other.getRef());// todo pay attention
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

  static class TransitionResultConsumer implements TransitionResultConsumerI {
    @Nullable private JsonSchemaObject mySchema;
    private boolean myAny;
    private boolean myNothing;

    public TransitionResultConsumer() {
      myNothing = true;
    }

    @Nullable
    public JsonSchemaObject getSchema() {
      return mySchema;
    }

    @Override
    public void setSchema(@Nullable JsonSchemaObject schema) {
      mySchema = schema;
      myNothing = schema == null;
    }

    public boolean isAny() {
      return myAny;
    }

    @Override
    public void anything() {
      myAny = true;
      myNothing = false;
    }

    public boolean isNothing() {
      return myNothing;
    }

    @Override
    public void nothing() {
      myNothing = true;
      myAny = false;
    }
  }

  public static class Step {
    private final StateType myType;
    @Nullable
    private final Transition myTransition;

    public Step(StateType type, @Nullable Transition transition) {
      myType = type;
      myTransition = transition;
    }

    public StateType getType() {
      return myType;
    }

    @Nullable
    public Transition getTransition() {
      return myTransition;
    }

    @Override
    public String toString() {
      String format = "?%s";
      if (StateType._object.equals(myType)) format = "{%s}";
      if (StateType._array.equals(myType)) format = "[%s]";
      if (StateType._value.equals(myType)) format = "#%s";
      return String.format(format, myTransition);
    }
  }

  public static class PropertyTransition implements Transition {
    @NotNull private final String myName;

    public PropertyTransition(@NotNull String name) {
      myName = name;
    }

    @Override
    public void step(@NotNull JsonSchemaObject parent,
                     @NotNull TransitionResultConsumerI resultConsumer,
                     boolean acceptAdditionalPropertiesSchemas) {
      if (JsonSchemaObject.DEFINITIONS.equals(myName)) {
        if (parent.getDefinitions() != null) {
          final SmartPsiElementPointer<JsonObject> pointer = parent.getDefinitionsPointer();
          final JsonSchemaObject object = new JsonSchemaObject(pointer);
          object.setProperties(parent.getDefinitions());
          resultConsumer.setSchema(object);
          return;
        }
      }
      final JsonSchemaObject child = parent.getProperties().get(myName);
      if (child != null) {
        resultConsumer.setSchema(child);
      } else {
        final JsonSchemaObject schema = parent.getMatchingPatternPropertySchema(myName);
        if (schema != null) {
          resultConsumer.setSchema(schema);
          return;
        }
        if (parent.getAdditionalPropertiesSchema() != null) {
          if (acceptAdditionalPropertiesSchemas) {
            resultConsumer.setSchema(parent.getAdditionalPropertiesSchema());
          }
        } else {
          if (!Boolean.FALSE.equals(parent.getAdditionalPropertiesAllowed())) {
            resultConsumer.anything();
          } else {
            resultConsumer.nothing();
          }
        }
      }
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  public static class ArrayTransition implements Transition {
    private final int myIdx;

    public ArrayTransition(int idx) {
      myIdx = idx;
    }

    @Override
    public void step(@NotNull JsonSchemaObject parent,
                     @NotNull TransitionResultConsumerI resultConsumer,
                     boolean acceptAdditionalPropertiesSchemas) {
      if (parent.getItemsSchema() != null) {
        resultConsumer.setSchema(parent.getItemsSchema());
      } else if (parent.getItemsSchemaList() != null) {
        final List<JsonSchemaObject> list = parent.getItemsSchemaList();
        if (myIdx >= 0 && myIdx < list.size()) {
          resultConsumer.setSchema(list.get(myIdx));
        } else if (parent.getAdditionalItemsSchema() != null) {
          resultConsumer.setSchema(parent.getAdditionalItemsSchema());
        } else if (!Boolean.FALSE.equals(parent.getAdditionalItemsAllowed())) {
          resultConsumer.anything();
        } else {
          resultConsumer.nothing();
        }
      }
    }

    @Override
    public String toString() {
      return String.valueOf(myIdx);
    }
  }
}
