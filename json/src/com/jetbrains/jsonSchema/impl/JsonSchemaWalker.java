package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Irina.Chernushina on 10/22/2015.
 *
 * We will be using following simplification:
 * for all-of conditions,
 * 1) we will only check that we can move forward through one of the links
 * and will be selecting first applicable link
 * 2) it will be totally checked only on last level
 *
 * this would break the following scheme:
 * "all-of" [{"properties" : {
 *   "inner": {"type": "string"}
 * }},
 * {"properties": {
 *   "inner": {"enum": ["one", "two"]}
 * }}]
 *
 * but this construct seems not very realistic for human writing...
 * seems better using "$ref"
 *
 * This will significantly simplify code, since we will not need to take the list of possible variants of children and grandchildren;
 * we will be able to iterate variants, not collections of variants
 */
public class JsonSchemaWalker {
  public interface CompletionSchemesConsumer {
    void consume(boolean isName, @NotNull JsonSchemaObject schema);
  }

  public static void findSchemasForAnnotation(@NotNull final PsiElement element, @NotNull final CompletionSchemesConsumer consumer,
                                              @NotNull final JsonSchemaObject rootSchema) {
    final List<Step> position = findPosition(element, false);
    if (position == null || position.isEmpty()) return;

    extractSchemaVariants(consumer, rootSchema, false, position);
  }

  public static void findSchemasForCompletion(@NotNull final PsiElement element, @NotNull final CompletionSchemesConsumer consumer,
                                              @NotNull final JsonSchemaObject rootSchema) {
    final PsiElement checkable = goUpToCheckable(element);
    if (checkable == null) return;
    final boolean isName = isName(checkable);
    final List<Step> position = findPosition(checkable, isName);
    if (position == null || position.isEmpty()) return;

    extractSchemaVariants(consumer, rootSchema, isName, position);
  }

  public static void extractSchemaVariants(@NotNull CompletionSchemesConsumer consumer,
                                            @NotNull JsonSchemaObject rootSchema, boolean isName, List<Step> position) {
    final ArrayDeque<Pair<JsonSchemaObject, Integer>> queue = new ArrayDeque<>();
    queue.add(Pair.create(rootSchema, 0));
    while (!queue.isEmpty()) {
      final Pair<JsonSchemaObject, Integer> pair = queue.removeFirst();

      final JsonSchemaObject schema = pair.getFirst();
      final Step step = position.get(pair.getSecond());
      if (step.getTransition() == null || (pair.getSecond() == (position.size() - 1))) {
        consumer.consume(isName, schema);
        continue;
      }
      if (step.getTransition() != null && !step.getTransition().possibleFromState(step.getType())) continue;

      final Condition<JsonSchemaObject> byTypeFilter = object -> byStateType(step.getType(), object);
      // not??

      if (schema.getAllOf() != null) {
        List<JsonSchemaObject> andList = ContainerUtil.filter(schema.getAllOf(), byTypeFilter);
        final TransitionResultConsumer transitionResultConsumer = new TransitionResultConsumer();
        JsonSchemaObject selectedSchema = null;
        for (JsonSchemaObject object : andList) {
          step.getTransition().step(object, transitionResultConsumer);
          if (transitionResultConsumer.isNothing()) continue;
          if (selectedSchema == null) {
            selectedSchema = transitionResultConsumer.getSchema();
          }
        }
        if (selectedSchema != null) {
          queue.add(Pair.create(selectedSchema, pair.getSecond() + 1));
        }
      } else {
        List<JsonSchemaObject> list = new ArrayList<>();
        list.add(schema);
        if (schema.getAnyOf() != null) list.addAll(schema.getAnyOf());
        if (schema.getOneOf() != null) list.addAll(schema.getOneOf());

        list = ContainerUtil.filter(list, byTypeFilter);
        for (JsonSchemaObject object : list) {
          final TransitionResultConsumer transitionResultConsumer = new TransitionResultConsumer();
          step.getTransition().step(object, transitionResultConsumer);
          // nothing or anything does not contribute to competion
          if (transitionResultConsumer.getSchema() != null) {
            queue.add(Pair.create(transitionResultConsumer.getSchema(), pair.getSecond() + 1));
          }
        }
      }
    }
  }

  private static boolean byStateType(@NotNull final StateType type, @NotNull final JsonSchemaObject schema) {
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

  private static boolean isName(PsiElement checkable) {
    final PsiElement parent = checkable.getParent();
    if (parent instanceof JsonObject) {
      return true;
    } else if (parent instanceof JsonProperty) {
      return PsiTreeUtil.isAncestor(((JsonProperty)parent).getNameElement(), checkable, false);
    }
    return false;
  }

  @Nullable
  private static PsiElement goUpToCheckable(@NotNull final PsiElement element) {
    PsiElement current = element;
    while (current != null && !(current instanceof PsiFile)) {
      if (current instanceof JsonValue) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  public static List<Step> findPosition(@NotNull final PsiElement element, boolean isName) {
    final List<Step> steps = new ArrayList<>();
    if (!(element.getParent() instanceof JsonObject) && !isName) {
      steps.add(new Step(StateType._value, null));
    }
    PsiElement current = element;
    while (! (current instanceof PsiFile)) {
      final PsiElement position = current;
      current = current.getParent();
      if (current instanceof JsonArray) {
        JsonArray array = (JsonArray)current;
        final List<JsonValue> list = array.getValueList();
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
          final JsonValue value = list.get(i);
          if (value.equals(position)) {
            idx = i;
            break;
          }
        }
        steps.add(new Step(StateType._array, new ArrayTransition(idx)));
      } else if (current instanceof JsonProperty) {
        final String propertyName = ((JsonProperty)current).getName();
        current = current.getParent();
        if (!(current instanceof JsonObject)) return null;//incorrect syntax?
        steps.add(new Step(StateType._object, new PropertyTransition(propertyName)));
      } else if (current instanceof PsiFile) {
        break;
      } else {
        return null;//something went wrong
      }
    }
    Collections.reverse(steps);
    return steps;
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
  }

  private static class PropertyTransition implements Transition {
    @NotNull private final String myName;

    private PropertyTransition(@NotNull String name) {
      myName = name;
    }

    @Override
    public boolean possibleFromState(@NotNull StateType stateType) {
      return StateType._object.equals(stateType);
    }

    @Override
    public void step(@NotNull JsonSchemaObject parent, @NotNull TransitionResultConsumer resultConsumer) {
      final JsonSchemaObject child = parent.getProperties().get(myName);
      if (child != null) {
        resultConsumer.setSchema(child);
      } else {
        if (parent.getAdditionalPropertiesSchema() != null) {
          resultConsumer.setSchema(parent.getAdditionalPropertiesSchema());
        } else {
          if (!Boolean.FALSE.equals(parent.getAdditionalPropertiesAllowed())) {
            resultConsumer.anything();
          } else {
            resultConsumer.nothing();
          }
        }
      }
    }
  }

  private static class ArrayTransition implements Transition {
    private final int myIdx;

    private ArrayTransition(int idx) {
      myIdx = idx;
    }

    @Override
    public boolean possibleFromState(@NotNull StateType stateType) {
      return StateType._array.equals(stateType);
    }

    @Override
    public void step(@NotNull JsonSchemaObject parent, @NotNull TransitionResultConsumer resultConsumer) {
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
  }

  private interface Transition {
    boolean possibleFromState(@NotNull StateType stateType);
    void step(@NotNull JsonSchemaObject parent, @NotNull TransitionResultConsumer resultConsumer);
  }

  private enum StateType {
    _object(JsonSchemaType._object), _array(JsonSchemaType._array), _value(null);

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

  private static class TransitionResultConsumer {
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

    public void setSchema(@Nullable JsonSchemaObject schema) {
      mySchema = schema;
      myNothing = schema == null;
    }

    public boolean isAny() {
      return myAny;
    }

    public void anything() {
      myAny = true;
      myNothing = false;
    }

    public boolean isNothing() {
      return myNothing;
    }

    public void nothing() {
      myNothing = true;
      myAny = false;
    }
  }
}
