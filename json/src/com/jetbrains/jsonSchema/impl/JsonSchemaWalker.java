package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonObject;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

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

  public static final JsonOriginalPsiWalker JSON_ORIGINAL_PSI_WALKER = new JsonOriginalPsiWalker();

  public interface CompletionSchemesConsumer {
    void consume(boolean isName,
                 @NotNull JsonSchemaObject schema,
                 @NotNull VirtualFile schemaFile,
                 @NotNull List<Step> steps);
  }

  public static void findSchemasForAnnotation(@NotNull final PsiElement element,
                                              @NotNull JsonLikePsiWalker walker,
                                              @NotNull final CompletionSchemesConsumer consumer,
                                              @NotNull final JsonSchemaObject rootSchema,
                                              @NotNull VirtualFile schemaFile) {
    final List<Step> position = walker.findPosition(element, false, true);
    if (position == null || position.isEmpty()) return;
    // but this does not validate definitions section against general schema --> should be done separately
    if (JsonSchemaFileType.INSTANCE.equals(element.getContainingFile().getFileType()) &&
        position.get(0).getTransition() instanceof PropertyTransition &&
        "definitions".equals(((PropertyTransition)position.get(0).getTransition()).getName())) return;
    extractSchemaVariants(element.getProject(), consumer, schemaFile, rootSchema, false, position, true);
  }

  public static void findSchemasForCompletion(@NotNull final PsiElement element,
                                              @NotNull JsonLikePsiWalker walker,
                                              @NotNull final CompletionSchemesConsumer consumer,
                                              @NotNull final JsonSchemaObject rootSchema,
                                              @NotNull VirtualFile schemaFile) {
    final PsiElement checkable = walker.goUpToCheckable(element);
    if (checkable == null) return;
    final boolean isName = walker.isName(checkable);
    final List<Step> position = walker.findPosition(checkable, isName, !isName);
    if (position == null || position.isEmpty()) {
      if (isName) consumer.consume(true, rootSchema, schemaFile, Collections.emptyList());
      return;
    }

    extractSchemaVariants(element.getProject(), consumer, schemaFile, rootSchema, isName, position, false);
  }

  public static void findSchemasForDocumentation(@NotNull final PsiElement element,
                                              @NotNull JsonLikePsiWalker walker,
                                              @NotNull final CompletionSchemesConsumer consumer,
                                              @NotNull final JsonSchemaObject rootSchema,
                                              @NotNull VirtualFile schemaFile) {
    final PsiElement checkable = walker.goUpToCheckable(element);
    if (checkable == null) return;
    final List<Step> position = walker.findPosition(checkable, true, true);
    if (position == null || position.isEmpty()) {
      consumer.consume(true, rootSchema, schemaFile, Collections.emptyList());
      return;
    }

    extractSchemaVariants(element.getProject(), consumer, schemaFile, rootSchema, true, position, false);
  }

  public static Pair<List<Step>, String> buildSteps(@NotNull String nameInSchema) {
    final String[] chain = JsonSchemaExportedDefinitions.normalizeId(nameInSchema).replace("\\", "/").split("/");
    final List<Step> steps = Arrays.stream(chain).filter(s -> !s.isEmpty()).map(item -> new Step(StateType._unknown, new PropertyTransition(item)))
      .collect(Collectors.toList());
    return Pair.create(steps, chain[chain.length - 1]);
  }

  protected static class DefinitionsResolver {
    @NotNull private final List<Step> myPosition;
    final List<Pair<JsonSchemaObject, List<Step>>> myVariants;
    private JsonSchemaObject mySchemaObject;

    public DefinitionsResolver(@NotNull List<Step> position) {
      myPosition = position;
      myVariants = new ArrayList<>();
    }

    public void consumeResult(@NotNull JsonSchemaObject schema) {
      mySchemaObject = schema;
    }

    public void consumeSmallStep(@NotNull JsonSchemaObject schema, int idx) {
      final List<JsonSchemaObject> list = gatherSchemas(schema);
      for (JsonSchemaObject object : list) {
        if (!StringUtil.isEmptyOrSpaces(object.getDefinitionAddress())) {
          myVariants.add(Pair.create(object, myPosition.subList(idx + 1, myPosition.size())));
        }
      }
    }

    public boolean isFound() {
      return mySchemaObject != null;
    }

    public JsonSchemaObject getSchemaObject() {
      return mySchemaObject;
    }

    public List<Pair<JsonSchemaObject, List<Step>>> getVariants() {
      return myVariants;
    }
  }

  public static void extractSchemaVariants(@NotNull final Project project,
                                           @NotNull final CompletionSchemesConsumer consumer,
                                           @NotNull VirtualFile rootSchemaFile,
                                           @NotNull JsonSchemaObject rootSchema,
                                           boolean isName,
                                           List<Step> position,
                                           boolean acceptAdditionalPropertiesSchemas) {
    final Set<Trinity<JsonSchemaObject, VirtualFile, List<Step>>> control = new HashSet<>();
    final JsonSchemaServiceEx serviceEx = JsonSchemaServiceEx.Impl.getEx(project);
    final ArrayDeque<Trinity<JsonSchemaObject, VirtualFile, List<Step>>> queue = new ArrayDeque<>();
    queue.add(Trinity.create(rootSchema, rootSchemaFile, position));
    while (!queue.isEmpty()) {
      final Trinity<JsonSchemaObject, VirtualFile, List<Step>> trinity = queue.removeFirst();
      if (!control.add(trinity)) break;
      final JsonSchemaObject object = trinity.getFirst();
      final VirtualFile schemaFile = trinity.getSecond();
      final List<Step> path = trinity.getThird();

      if (path.isEmpty()) {
        consumer.consume(isName, object, schemaFile, path);
        continue;
      }
      final DefinitionsResolver definitionsResolver = new DefinitionsResolver(path);
      extractSchemaVariants(definitionsResolver, object, path, acceptAdditionalPropertiesSchemas);

      if (definitionsResolver.isFound()) {
        final List<JsonSchemaObject> list = gatherSchemas(definitionsResolver.getSchemaObject());
        for (JsonSchemaObject schemaObject : list) {
          if (schemaObject.getDefinitionAddress() != null) {
            final List<Step> steps = new ArrayList<>();
            // add value step if needed
            if (!isName) steps.add(new Step(StateType._value, null));
            visitSchemaByDefinitionAddress(serviceEx, queue, schemaFile, schemaObject.getDefinitionAddress(), steps);
          }
        }
        consumer.consume(isName, definitionsResolver.getSchemaObject(), schemaFile, path);
      } else {
        final List<Pair<JsonSchemaObject, List<Step>>> variants = definitionsResolver.getVariants();
        for (Pair<JsonSchemaObject, List<Step>> variant : variants) {
          if (variant.getFirst().getDefinitionAddress() == null) continue;
          visitSchemaByDefinitionAddress(serviceEx, queue, schemaFile, variant.getFirst().getDefinitionAddress(), variant.getSecond());
        }
      }
    }
  }

  private static void visitSchemaByDefinitionAddress(JsonSchemaServiceEx serviceEx,
                                                     ArrayDeque<Trinity<JsonSchemaObject, VirtualFile, List<Step>>> queue,
                                                     VirtualFile schemaFile, @NotNull final String definitionAddress, final List<Step> steps) {
    // we can have also non-absolute transfers here, because allOf and others can not be put in-place into schema
    final JsonSchemaReader.SchemaUrlSplitter splitter = new JsonSchemaReader.SchemaUrlSplitter(definitionAddress);
    //noinspection ConstantConditions
    final VirtualFile variantSchemaFile = splitter.isAbsolute() ? serviceEx.getSchemaFileById(splitter.getSchemaId(), schemaFile) :
      schemaFile;
    if (variantSchemaFile == null) return;
    serviceEx.visitSchemaObject(variantSchemaFile,
                                variantObject -> {
                                  final List<Step> variantSteps = buildSteps(splitter.getRelativePath()).getFirst();
                                  variantSteps.addAll(steps);
                                  queue.add(Trinity.create(variantObject, variantSchemaFile, variantSteps));
                                  return true;
                                });
  }

  private static void extractSchemaVariants(@NotNull DefinitionsResolver consumer,
                                            @NotNull JsonSchemaObject rootSchema,
                                            @NotNull List<Step> position,
                                            boolean acceptAdditionalPropertiesSchemas) {
    final ArrayDeque<Pair<JsonSchemaObject, Integer>> queue = new ArrayDeque<>();
    queue.add(Pair.create(rootSchema, 0));
    while (!queue.isEmpty()) {
      final Pair<JsonSchemaObject, Integer> pair = queue.removeFirst();

      final JsonSchemaObject schema = pair.getFirst();
      final Integer level = pair.getSecond();
      if (position.size() <= level) {
        return;
      }
      final Step step = position.get(level);
      if (step.getTransition() == null) {
        consumer.consumeResult(schema);
        continue;
      }
      if (step.getTransition() != null && !StateType._unknown.equals(step.getType())
          && !step.getTransition().possibleFromState(step.getType())) continue;

      final Condition<JsonSchemaObject> byTypeFilter = object -> byStateType(step.getType(), object);
      // not??

      List<JsonSchemaObject> list = gatherSchemas(schema);
      list = ContainerUtil.filter(list, byTypeFilter);

      final Consumer<JsonSchemaObject> reporter = object -> {
        if ((level + 1) >= position.size()) consumer.consumeResult(object);
        else {
          consumer.consumeSmallStep(object, level);
          queue.add(Pair.create(object, level + 1));
        }
      };

      TransitionResultConsumer transitionResultConsumer = new TransitionResultConsumer();
      for (JsonSchemaObject object : list) {
        if (schema.getAllOf() == null) transitionResultConsumer = new TransitionResultConsumer();
        step.getTransition().step(object, transitionResultConsumer, acceptAdditionalPropertiesSchemas);
        if (transitionResultConsumer.isNothing()) continue;
        if (transitionResultConsumer.getSchema() != null) {
          reporter.consume(transitionResultConsumer.getSchema());
        }
      }
    }
  }

  private static List<JsonSchemaObject> gatherSchemas(JsonSchemaObject schema) {
    List<JsonSchemaObject> list = new ArrayList<>();
    list.add(schema);
    if (schema.getAllOf() != null) {
      list.addAll(schema.getAllOf());
    } else {
      if (schema.getAnyOf() != null) list.addAll(schema.getAnyOf());
      if (schema.getOneOf() != null) list.addAll(schema.getOneOf());

    }
    return list;
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
  }

  public static class PropertyTransition implements Transition {
    @NotNull private final String myName;

    public PropertyTransition(@NotNull String name) {
      myName = name;
    }

    @Override
    public boolean possibleFromState(@NotNull StateType stateType) {
      return StateType._object.equals(stateType);
    }

    @Override
    public void step(@NotNull JsonSchemaObject parent,
                     @NotNull TransitionResultConsumer resultConsumer,
                     boolean acceptAdditionalPropertiesSchemas) {
      if ("definitions".equals(myName)) {
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
  }

  public static class ArrayTransition implements Transition {
    private final int myIdx;

    public ArrayTransition(int idx) {
      myIdx = idx;
    }

    @Override
    public boolean possibleFromState(@NotNull StateType stateType) {
      return StateType._array.equals(stateType);
    }

    @Override
    public void step(@NotNull JsonSchemaObject parent,
                     @NotNull TransitionResultConsumer resultConsumer,
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
  }

  private interface Transition {
    boolean possibleFromState(@NotNull StateType stateType);
    void step(@NotNull JsonSchemaObject parent, @NotNull TransitionResultConsumer resultConsumer, boolean acceptAdditionalPropertiesSchemas);
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
