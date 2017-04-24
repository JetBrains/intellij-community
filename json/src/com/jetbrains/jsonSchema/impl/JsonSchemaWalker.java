package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonObject;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 10/22/2015.
 */
public class JsonSchemaWalker {

  public static final JsonOriginalPsiWalker JSON_ORIGINAL_PSI_WALKER = new JsonOriginalPsiWalker();

  @NotNull
  public static String normalizeId(@NotNull String id) {
    id = id.endsWith("#") ? id.substring(0, id.length() - 1) : id;
    return id.startsWith("#") ? id.substring(1) : id;
  }

  public static Pair<List<Step>, String> buildSteps(@NotNull String nameInSchema) {
    final List<String> chain = StringUtil.split(normalizeId(nameInSchema).replace("\\", "/"), "/");
    final List<Step> steps = chain.stream().filter(s -> !s.isEmpty()).map(item -> new Step(StateType._unknown, new PropertyTransition(item)))
      .collect(Collectors.toList());
    if (steps.isEmpty()) return Pair.create(Collections.emptyList(), nameInSchema);
    return Pair.create(steps, chain.get(chain.size() - 1));
  }

  public static JsonLikePsiWalker getWalker(@NotNull final PsiElement element, JsonSchemaObject schemaObject) {
    return getJsonLikeThing(element, walker -> walker, schemaObject);
  }

  @Nullable
  private static <T> T getJsonLikeThing(@NotNull final PsiElement element,
                                        @NotNull Convertor<JsonLikePsiWalker, T> convertor,
                                        JsonSchemaObject schemaObject) {
    final List<JsonLikePsiWalker> list = new ArrayList<>();
    list.add(JSON_ORIGINAL_PSI_WALKER);
    final JsonLikePsiWalkerFactory[] extensions = Extensions.getExtensions(JsonLikePsiWalkerFactory.EXTENSION_POINT_NAME);
    list.addAll(Arrays.stream(extensions).map(extension -> extension.create(schemaObject)).collect(Collectors.toList()));
    for (JsonLikePsiWalker walker : list) {
      if (walker.handles(element)) return convertor.convert(walker);
    }
    return null;
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

  public interface Transition {

    void step(@NotNull JsonSchemaObject parent, @NotNull TransitionResultConsumerI resultConsumer, boolean acceptAdditionalPropertiesSchemas);
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

  static class TransitionResultConsumer implements TransitionResultConsumerI {
    @Nullable private JsonSchemaObject mySchema;
    private boolean myAny;
    private boolean myNothing;
    private boolean myOneOf;

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

    @Override
    public void oneOf() {
      myOneOf = true;
    }

    public boolean isOneOf() {
      return myOneOf;
    }
  }
}
