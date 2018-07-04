// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.google.common.base.Predicates;
import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonContainer;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 4/25/2017.
 */
class JsonSchemaAnnotatorChecker {
  private static final Set<JsonSchemaType> PRIMITIVE_TYPES =
    ContainerUtil.set(JsonSchemaType._integer, JsonSchemaType._number, JsonSchemaType._boolean, JsonSchemaType._string, JsonSchemaType._null);
  private final Map<PsiElement, JsonValidationError> myErrors;
  private boolean myHadTypeError;

  protected JsonSchemaAnnotatorChecker() {
    myErrors = new HashMap<>();
  }

  public Map<PsiElement, JsonValidationError> getErrors() {
    return myErrors;
  }

  public boolean isHadTypeError() {
    return myHadTypeError;
  }

  public static JsonSchemaAnnotatorChecker checkByMatchResult(@NotNull JsonValueAdapter elementToCheck,
                                                              @NotNull final MatchResult result) {
    final List<JsonSchemaAnnotatorChecker> checkers = new ArrayList<>();
    if (result.myExcludingSchemas.isEmpty() && result.mySchemas.size() == 1) {
      final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker();
      checker.checkByScheme(elementToCheck, result.mySchemas.iterator().next());
      checkers.add(checker);
    }
    else {
      if (!result.mySchemas.isEmpty()) {
        checkers.add(processSchemasVariants(result.mySchemas, elementToCheck, false).getSecond());
      }
      if (!result.myExcludingSchemas.isEmpty()) {
        // we can have several oneOf groups, each about, for instance, a part of properties
        // - then we should allow properties from neighbour schemas (even if additionalProperties=false)
        final List<JsonSchemaObject> selectedSchemas = new SmartList<>();
        final List<JsonSchemaAnnotatorChecker> list = result.myExcludingSchemas.stream()
          .map(group -> {
            final Pair<JsonSchemaObject, JsonSchemaAnnotatorChecker> pair = processSchemasVariants(group, elementToCheck, true);
            if (pair.getFirst() != null) selectedSchemas.add(pair.getFirst());
            return pair.getSecond();
          }).collect(Collectors.toList());
        checkers.add(mergeErrors(list, selectedSchemas));
      }
    }
    if (checkers.isEmpty()) return null;
    if (checkers.size() == 1) return checkers.get(0);

    return checkers.stream()
      .filter(checker -> !checker.isHadTypeError())
      .findFirst()
      .orElse(checkers.get(0));
  }

  private static JsonSchemaAnnotatorChecker mergeErrors(@NotNull List<JsonSchemaAnnotatorChecker> list,
                                                        @NotNull List<JsonSchemaObject> selectedSchemas) {
    final Set<String> skipErrors = selectedSchemas.stream().filter(Predicates.notNull())
      .map(schema -> schema.getProperties().keySet())
      .flatMap(Set::stream).map(name -> JsonBundle.message("json.schema.annotation.not.allowed.property", name))
      .collect(Collectors.toSet());
    final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker();

    for (JsonSchemaAnnotatorChecker ch: list) {
      for (Map.Entry<PsiElement, JsonValidationError> element: ch.myErrors.entrySet()) {
        if (skipErrors.contains(element.getValue().getMessage())) {
          continue;
        }
        checker.myErrors.put(element.getKey(), element.getValue());
      }
    }
    return checker;
  }

  private void error(final String error, final PsiElement holder) {
    error(error, holder, JsonValidationError.FixableIssueKind.None, null);
  }

  private void error(final PsiElement newHolder, JsonValidationError error) {
    error(error.getMessage(), newHolder, error.getFixableIssueKind(), error.getIssueData());
  }

  private void error(final String error, final PsiElement holder, JsonValidationError.FixableIssueKind fixableIssueKind, JsonValidationError.IssueData data) {
    if (myErrors.containsKey(holder)) return;
    myErrors.put(holder, new JsonValidationError(error, fixableIssueKind, data));
  }

  private void typeError(final @NotNull PsiElement value, final @NotNull JsonSchemaType... allowedTypes) {
    if (allowedTypes.length > 0) {
      if (allowedTypes.length == 1) {
        error(String.format("Type is not allowed. Expected: %s.", allowedTypes[0].getName()), value,
              JsonValidationError.FixableIssueKind.ProhibitedType,
              new JsonValidationError.TypeMismatchIssueData(allowedTypes));
      } else {
        final String typesText = Arrays.stream(allowedTypes)
                                       .map(JsonSchemaType::getName)
                                       .distinct()
                                       .sorted(Comparator.naturalOrder())
                                       .collect(Collectors.joining(", "));
        error(String.format("Type is not allowed. Expected one of: %s.", typesText), value,
              JsonValidationError.FixableIssueKind.ProhibitedType,
              new JsonValidationError.TypeMismatchIssueData(allowedTypes));
      }
    } else {
      error("Type is not allowed", value);
    }
    myHadTypeError = true;
  }

  public void checkByScheme(@NotNull JsonValueAdapter value, @NotNull JsonSchemaObject schema) {
    final JsonSchemaType type = JsonSchemaType.getType(value);
    if (type != null) {
      JsonSchemaType schemaType = getMatchingSchemaType(schema, type);
      if (schemaType != null && !schemaType.equals(type)) {
        typeError(value.getDelegate(), schemaType);
      }
      else {
        if (JsonSchemaType._boolean.equals(type)) {
          checkForEnum(value.getDelegate(), schema);
        }
        else if (JsonSchemaType._string_number.equals(type)) {
          checkNumber(value.getDelegate(), schema, type);
          checkString(value.getDelegate(), schema);
          checkForEnum(value.getDelegate(), schema);
        }
        else if (JsonSchemaType._number.equals(type) || JsonSchemaType._integer.equals(type)) {
          checkNumber(value.getDelegate(), schema, type);
          checkForEnum(value.getDelegate(), schema);
        }
        else if (JsonSchemaType._string.equals(type)) {
          checkString(value.getDelegate(), schema);
          checkForEnum(value.getDelegate(), schema);
        }
        else if (JsonSchemaType._array.equals(type)) {
          checkArray(value, schema);
          checkForEnum(value.getDelegate(), schema);
        }
        else if (JsonSchemaType._object.equals(type)) {
          checkObject(value, schema);
          checkForEnum(value.getDelegate(), schema);
        }
      }
    }

    if ((!myHadTypeError || myErrors.isEmpty()) && !value.isShouldBeIgnored()) {
      PsiElement delegate = value.getDelegate();
      checkForEnum(delegate, schema);
      if (hasNumberChecks(schema) && value.isNumberLiteral()) {
        checkNumber(delegate, schema, JsonSchemaType._number);
      }
      if (hasStringChecks(schema) && value.isStringLiteral()) {
        checkString(delegate, schema);
      }
      if (hasArrayChecks(schema) && value.isArray()) {
        checkArray(value, schema);
      }
      if (hasMinMaxLengthChecks(schema)) {
        if (value.isStringLiteral()) {
          checkString(delegate, schema);
        }
        else if (value.isArray()) {
          checkArray(value, schema);
        }
      }
      if (hasObjectChecks(schema) && value.isObject()) {
        checkObject(value, schema);
      }
    }

    if (schema.getNot() != null) {
      final MatchResult result = new JsonSchemaResolver(schema.getNot()).detailedResolve();
      if (result.mySchemas.isEmpty() && result.myExcludingSchemas.isEmpty()) return;

      // if 'not' uses reference to owning schema back -> do not check, seems it does not make any sense
      if (result.mySchemas.stream().anyMatch(s -> schema.getJsonObject().equals(s.getJsonObject())) ||
          result.myExcludingSchemas.stream().flatMap(Collection::stream)
            .anyMatch(s -> schema.getJsonObject().equals(s.getJsonObject()))) return;

      final JsonSchemaAnnotatorChecker checker = checkByMatchResult(value, result);
      if (checker == null || checker.isCorrect()) error("Validates against 'not' schema", value.getDelegate());
    }

    if (schema.getIf() != null) {
      MatchResult result = new JsonSchemaResolver(schema.getIf()).detailedResolve();
      if (result.mySchemas.isEmpty() && result.myExcludingSchemas.isEmpty()) return;

      final JsonSchemaAnnotatorChecker checker = checkByMatchResult(value, result);
      if (checker != null) {
        if (checker.isCorrect()) {
          JsonSchemaObject then = schema.getThen();
          if (then == null) {
            error("Validates against 'if' branch but no 'then' branch is present", value.getDelegate());
          }
          else {
            checkObjectBySchemaRecordErrors(then, value);
          }
        }
        else {
          JsonSchemaObject schemaElse = schema.getElse();
          if (schemaElse == null) {
            error("Validates counter 'if' branch but no 'else' branch is present", value.getDelegate());
          }
          else {
            checkObjectBySchemaRecordErrors(schemaElse, value);
          }
        }
      }
    }
  }

  private void checkObjectBySchemaRecordErrors(@NotNull JsonSchemaObject schema, @NotNull JsonValueAdapter object) {
    final JsonSchemaAnnotatorChecker checker = checkByMatchResult(object, new JsonSchemaResolver(schema).detailedResolve());
    if (checker != null) {
      myHadTypeError = checker.isHadTypeError();
      myErrors.putAll(checker.getErrors());
    }
  }

  private static boolean hasObjectChecks(JsonSchemaObject schema) {
    return !schema.getProperties().isEmpty()
           || schema.getPropertyNamesSchema() != null
           || schema.getPropertyDependencies() != null
           || schema.hasPatternProperties()
           || schema.getRequired() != null
           || schema.getMinProperties() != null
           || schema.getMaxProperties() != null;
  }

  private void checkObject(@NotNull JsonValueAdapter value, @NotNull JsonSchemaObject schema) {
    final JsonObjectValueAdapter object = value.getAsObject();
    if (object == null) return;

    //noinspection ConstantConditions
    final List<JsonPropertyAdapter> propertyList = object.getPropertyList();
    final Set<String> set = new HashSet<>();
    for (JsonPropertyAdapter property : propertyList) {
      final String name = StringUtil.notNullize(property.getName());
      JsonSchemaObject propertyNamesSchema = schema.getPropertyNamesSchema();
      if (propertyNamesSchema != null) {
        JsonValueAdapter nameValueAdapter = property.getNameValueAdapter();
        if (nameValueAdapter != null) {
          checkByScheme(nameValueAdapter, propertyNamesSchema);
        }
      }

      final JsonSchemaVariantsTreeBuilder.Step step = JsonSchemaVariantsTreeBuilder.Step.createPropertyStep(name);
      final Pair<ThreeState, JsonSchemaObject> pair = step.step(schema, true);
      if (ThreeState.NO.equals(pair.getFirst()) && !set.contains(name)) {
        error(JsonBundle.message("json.schema.annotation.not.allowed.property", name), property.getDelegate(),
              JsonValidationError.FixableIssueKind.ProhibitedProperty,
              new JsonValidationError.ProhibitedPropertyIssueData(name));
      }
      else if (ThreeState.UNSURE.equals(pair.getFirst()) && property.getValue() != null) {
        checkObjectBySchemaRecordErrors(pair.getSecond(), property.getValue());
      }
      set.add(name);
    }

    if (object.shouldCheckIntegralRequirements()) {
      final List<String> required = schema.getRequired();
      if (required != null) {
        HashSet<String> requiredNames = ContainerUtil.newHashSet(required);
        requiredNames.removeAll(set);
        if (!requiredNames.isEmpty()) {
          JsonValidationError.MissingMultiplePropsIssueData data = createMissingPropertiesData(schema, requiredNames);
          error("Missing required " + data.getMessage(false), value.getDelegate(), JsonValidationError.FixableIssueKind.MissingProperty, data);
        }
      }
      if (schema.getMinProperties() != null && propertyList.size() < schema.getMinProperties()) {
        error("Number of properties is less than " + schema.getMinProperties(), value.getDelegate());
      }
      if (schema.getMaxProperties() != null && propertyList.size() > schema.getMaxProperties()) {
        error("Number of properties is greater than " + schema.getMaxProperties(), value.getDelegate());
      }
      final Map<String, List<String>> dependencies = schema.getPropertyDependencies();
      if (dependencies != null) {
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
          if (set.contains(entry.getKey())) {
            final List<String> list = entry.getValue();
            HashSet<String> deps = ContainerUtil.newHashSet(list);
            deps.removeAll(set);
            if (!deps.isEmpty()) {
              JsonValidationError.MissingMultiplePropsIssueData data = createMissingPropertiesData(schema, deps);
              error("Dependency is violated: " + data.getMessage(false) + " must be specified, since '" + entry.getKey() + "' is specified",
                    value.getDelegate(),
                    JsonValidationError.FixableIssueKind.MissingProperty,
                    data);
            }
          }
        }
      }
      final Map<String, JsonSchemaObject> schemaDependencies = schema.getSchemaDependencies();
      if (schemaDependencies != null) {
        for (Map.Entry<String, JsonSchemaObject> entry : schemaDependencies.entrySet()) {
          if (set.contains(entry.getKey())) {
            checkObjectBySchemaRecordErrors(entry.getValue(), value);
          }
        }
      }
    }

    validateAsJsonSchema(object.getDelegate());
  }

  @NotNull
  private static JsonValidationError.MissingMultiplePropsIssueData createMissingPropertiesData(@NotNull JsonSchemaObject schema,
                                                                                               HashSet<String> requiredNames) {
    List<JsonValidationError.MissingPropertyIssueData> allProps = ContainerUtil.newArrayList();
    for (String req: requiredNames) {
      JsonSchemaObject propertySchema = resolvePropertySchema(schema, req);
      allProps.add(new JsonValidationError.MissingPropertyIssueData(req,
                                                                    propertySchema == null ? null : propertySchema.getType(),
                                                                    propertySchema == null ? null : propertySchema.getDefault(),
                                                                    propertySchema != null && propertySchema.getEnum() != null));
    }

    return new JsonValidationError.MissingMultiplePropsIssueData(allProps);
  }

  private static JsonSchemaObject resolvePropertySchema(@NotNull JsonSchemaObject schema, String req) {
    if (schema.getProperties().containsKey(req)) {
      return schema.getProperties().get(req);
    }
    else {
      JsonSchemaObject propertySchema = schema.getMatchingPatternPropertySchema(req);
      if (propertySchema != null) {
        return propertySchema;
      }
      else {
        JsonSchemaObject additionalPropertiesSchema = schema.getAdditionalPropertiesSchema();
        if (additionalPropertiesSchema != null) {
          return additionalPropertiesSchema;
        }
      }
    }
    return null;
  }

  private void validateAsJsonSchema(@NotNull PsiElement objElement) {
    final JsonObject object = ObjectUtils.tryCast(objElement, JsonObject.class);
    if (object == null) return;

    if (!JsonSchemaService.isSchemaFile(objElement.getContainingFile())) {
      return;
    }

    final VirtualFile schemaFile = object.getContainingFile().getVirtualFile();
    if (schemaFile == null) return;

    final JsonSchemaObject schemaObject = JsonSchemaService.Impl.get(object.getProject()).getSchemaObjectForSchemaFile(schemaFile);
    if (schemaObject == null) return;

    final List<JsonSchemaVariantsTreeBuilder.Step> position = JsonOriginalPsiWalker.INSTANCE.findPosition(object, true);
    if (position == null) return;
    final List<JsonSchemaVariantsTreeBuilder.Step> steps = skipProperties(position);
    // !! not root schema, because we validate the schema written in the file itself
    final MatchResult result = new JsonSchemaResolver(schemaObject, false, steps).detailedResolve();
    for (JsonSchemaObject s: result.mySchemas) {
      reportInvalidPatternProperties(s);
      reportPatternErrors(s);
    }
    result.myExcludingSchemas.stream().flatMap(Collection::stream).filter(s -> schemaFile.equals(s.getSchemaFile()))
      .forEach(schema -> {
        reportInvalidPatternProperties(schema);
        reportPatternErrors(schema);
      });
  }

  private void reportPatternErrors(JsonSchemaObject schema) {
    for (JsonSchemaObject prop : schema.getProperties().values()) {
      final String patternError = prop.getPatternError();
      if (patternError == null || prop.getPattern() == null) {
        continue;
      }

      final JsonContainer element = prop.getJsonObject();
      if (!(element instanceof JsonObject) || !element.isValid()) {
        continue;
      }

      final JsonProperty pattern = ((JsonObject)element).findProperty("pattern");
      if (pattern != null) {
        error(StringUtil.convertLineSeparators(patternError), pattern.getValue());
      }
    }
  }

  private void reportInvalidPatternProperties(JsonSchemaObject schema) {
    final Map<JsonContainer, String> invalidPatternProperties = schema.getInvalidPatternProperties();
    if (invalidPatternProperties == null) return;

    for (Map.Entry<JsonContainer, String> entry : invalidPatternProperties.entrySet()) {
      final JsonContainer element = entry.getKey();
      if (element == null || !element.isValid()) continue;
      final PsiElement parent = element.getParent();
      if (parent instanceof JsonProperty) {
        error(StringUtil.convertLineSeparators(entry.getValue()), ((JsonProperty)parent).getNameElement());
      }
    }
  }

  private static List<JsonSchemaVariantsTreeBuilder.Step> skipProperties(List<JsonSchemaVariantsTreeBuilder.Step> position) {
    final Iterator<JsonSchemaVariantsTreeBuilder.Step> iterator = position.iterator();
    boolean canSkip = true;
    while (iterator.hasNext()) {
      final JsonSchemaVariantsTreeBuilder.Step step = iterator.next();
      if (canSkip && step.isFromObject() && JsonSchemaObject.PROPERTIES.equals(step.getName())) {
        iterator.remove();
        canSkip = false;
      }
      else {
        canSkip = true;
      }
    }
    return position;
  }

  private void checkForEnum(PsiElement value, JsonSchemaObject schema) {
    //enum values + pattern -> don't check enum values
    if (schema.getEnum() == null || schema.getPattern() != null) return;
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(value, schema);
    if (walker == null) return;
    final String text = StringUtil.notNullize(walker.getNodeTextForValidation(value));
    final List<Object> objects = schema.getEnum();
    for (Object object : objects) {
      if (walker.onlyDoubleQuotesForStringLiterals()) {
        if (object.toString().equalsIgnoreCase(text)) return;
      }
      else {
        if (equalsIgnoreQuotesAndCase(object.toString(), text, walker.quotesForStringLiterals())) return;
      }
    }
    error("Value should be one of: [" + StringUtil.join(objects, o -> o.toString(), ", ") + "]", value,
          JsonValidationError.FixableIssueKind.NonEnumValue, null);
  }

  private static boolean equalsIgnoreQuotesAndCase(@NotNull final String s1, @NotNull final String s2, boolean requireQuotedValues) {
    final boolean quoted1 = StringUtil.isQuotedString(s1);
    final boolean quoted2 = StringUtil.isQuotedString(s2);
    if (requireQuotedValues && quoted1 != quoted2) return false;
    if (requireQuotedValues && !quoted1) return s1.equalsIgnoreCase(s2);
    return StringUtil.unquoteString(s1).equalsIgnoreCase(StringUtil.unquoteString(s2));
  }

  private void checkArray(JsonValueAdapter value, JsonSchemaObject schema) {
    final JsonArrayValueAdapter asArray = value.getAsArray();
    if (asArray == null) return;
    final List<JsonValueAdapter> elements = asArray.getElements();
    if (schema.getMinLength() != null && elements.size() < schema.getMinLength()) {
      error("Array is shorter than " + schema.getMinLength(), value.getDelegate());
      return;
    }
    checkArrayItems(value, elements, schema);
  }

  @NotNull
  private static Pair<JsonSchemaObject, JsonSchemaAnnotatorChecker> processSchemasVariants(
    @NotNull final Collection<? extends JsonSchemaObject> collection,
    @NotNull final JsonValueAdapter value, boolean isOneOf) {

    final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker();
    final JsonSchemaType type = JsonSchemaType.getType(value);
    JsonSchemaObject selected = null;
    if (type == null) {
      if (!value.isShouldBeIgnored()) checker.typeError(value.getDelegate(), getExpectedTypes(collection));
    }
    else {
      final List<JsonSchemaObject> filtered = ContainerUtil.newArrayListWithCapacity(collection.size());
      for (JsonSchemaObject schema: collection) {
        if (!areSchemaTypesCompatible(schema, type)) continue;
        filtered.add(schema);
      }
      if (filtered.isEmpty()) checker.typeError(value.getDelegate(), getExpectedTypes(collection));
      else if (filtered.size() == 1) {
        selected = filtered.get(0);
        checker.checkByScheme(value, selected);
      }
      else {
        if (isOneOf) {
          selected = checker.processOneOf(value, filtered);
        }
        else {
          selected = checker.processAnyOf(value, filtered);
        }
      }
    }
    return Pair.create(selected, checker);
  }

  private final static JsonSchemaType[] NO_TYPES = new JsonSchemaType[0];
  private static JsonSchemaType[] getExpectedTypes(final Collection<? extends JsonSchemaObject> schemas) {
    final List<JsonSchemaType> list = new ArrayList<>();
    for (JsonSchemaObject schema : schemas) {
      final JsonSchemaType type = schema.getType();
      if (type != null) {
        list.add(type);
      } else {
        final List<JsonSchemaType> variants = schema.getTypeVariants();
        if (variants != null) {
          list.addAll(variants);
        }
      }
    }
    return list.isEmpty() ? NO_TYPES : list.toArray(NO_TYPES);
  }

  public static boolean areSchemaTypesCompatible(@NotNull final JsonSchemaObject schema, @NotNull final JsonSchemaType type) {
    final JsonSchemaType matchingSchemaType = getMatchingSchemaType(schema, type);
    if (matchingSchemaType != null) return matchingSchemaType.equals(type);
    if (schema.getEnum() != null) {
      return PRIMITIVE_TYPES.contains(type);
    }
    return true;
  }

  @Nullable
  private static JsonSchemaType getMatchingSchemaType(@NotNull JsonSchemaObject schema, @NotNull JsonSchemaType input) {
    if (schema.getType() != null) {
      final JsonSchemaType matchType = schema.getType();
      if (matchType != null) {
        if (JsonSchemaType._integer.equals(input) && JsonSchemaType._number.equals(matchType)) {
          return input;
        }
        if (JsonSchemaType._string_number.equals(input) && (JsonSchemaType._number.equals(matchType)
                                                            || JsonSchemaType._integer.equals(matchType)
                                                            || JsonSchemaType._string.equals(matchType))) {
          return input;
        }
        return matchType;
      }
    }
    if (schema.getTypeVariants() != null) {
      List<JsonSchemaType> matchTypes = schema.getTypeVariants();
      if (matchTypes.contains(input)) {
        return input;
      }
      if (JsonSchemaType._integer.equals(input) && matchTypes.contains(JsonSchemaType._number)) {
        return input;
      }
      //nothing matches, lets return one of the list so that other heuristics does not match
      return matchTypes.get(0);
    }
    if (!schema.getProperties().isEmpty() && JsonSchemaType._object.equals(input)) return JsonSchemaType._object;
    return null;
  }

  private static boolean hasArrayChecks(JsonSchemaObject schema) {
    return schema.isUniqueItems()
           || schema.getContainsSchema() != null
           || schema.getItemsSchema() != null
           || schema.getItemsSchemaList() != null
           || schema.getMinItems() != null
           || schema.getMaxItems() != null;
  }

  private void checkArrayItems(@NotNull JsonValueAdapter array, @NotNull final List<JsonValueAdapter> list, final JsonSchemaObject schema) {
    if (schema.isUniqueItems()) {
      final MultiMap<String, JsonValueAdapter> valueTexts = new MultiMap<>();
      final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(array.getDelegate(), schema);
      assert walker != null;
      for (JsonValueAdapter adapter : list) {
        valueTexts.putValue(walker.getNodeTextForValidation(adapter.getDelegate()), adapter);
      }

      for (Map.Entry<String, Collection<JsonValueAdapter>> entry: valueTexts.entrySet()) {
        if (entry.getValue().size() > 1) {
          for (JsonValueAdapter item: entry.getValue()) {
            error("Item is not unique", item.getDelegate());
          }
        }
      }
    }
    if (schema.getContainsSchema() != null) {
      boolean match = false;
      for (JsonValueAdapter item: list) {
        final JsonSchemaAnnotatorChecker checker = checkByMatchResult(item, new JsonSchemaResolver(schema.getContainsSchema()).detailedResolve());
        if (checker == null || checker.myErrors.size() == 0 && !checker.myHadTypeError) {
          match = true;
          break;
        }
      }
      if (!match) {
        error("No match for 'contains' rule", array.getDelegate());
      }
    }
    if (schema.getItemsSchema() != null) {
      for (JsonValueAdapter item : list) {
        checkObjectBySchemaRecordErrors(schema.getItemsSchema(), item);
      }
    }
    else if (schema.getItemsSchemaList() != null) {
      final Iterator<JsonSchemaObject> iterator = schema.getItemsSchemaList().iterator();
      for (JsonValueAdapter arrayValue : list) {
        if (iterator.hasNext()) {
          checkObjectBySchemaRecordErrors(iterator.next(), arrayValue);
        }
        else {
          if (!Boolean.TRUE.equals(schema.getAdditionalItemsAllowed())) {
            error("Additional items are not allowed", arrayValue.getDelegate());
          }
          else if (schema.getAdditionalItemsSchema() != null) {
            checkObjectBySchemaRecordErrors(schema.getAdditionalItemsSchema(), arrayValue);
          }
        }
      }
    }
    if (schema.getMinItems() != null && list.size() < schema.getMinItems()) {
      error("Array is shorter than " + schema.getMinItems(), array.getDelegate());
    }
    if (schema.getMaxItems() != null && list.size() > schema.getMaxItems()) {
      error("Array is longer than " + schema.getMaxItems(), array.getDelegate());
    }
  }

  private static boolean hasStringChecks(JsonSchemaObject schema) {
    return schema.getPattern() != null || schema.getFormat() != null;
  }

  private static boolean hasMinMaxLengthChecks(JsonSchemaObject schema) {
    return schema.getMinLength() != null || schema.getMaxLength() != null;
  }

  private void checkString(PsiElement propValue, JsonSchemaObject schema) {
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(propValue, schema);
    assert walker != null;
    final String value = StringUtil.unquoteString(walker.getNodeTextForValidation(propValue));
    if (schema.getMinLength() != null) {
      if (value.length() < schema.getMinLength()) {
        error("String is shorter than " + schema.getMinLength(), propValue);
        return;
      }
    }
    if (schema.getMaxLength() != null) {
      if (value.length() > schema.getMaxLength()) {
        error("String is longer than " + schema.getMaxLength(), propValue);
        return;
      }
    }
    if (schema.getPattern() != null) {
      if (schema.getPatternError() != null) {
        error("Can not check string by pattern because of error: " + StringUtil.convertLineSeparators(schema.getPatternError()),
              propValue);
      }
      if (!schema.checkByPattern(value)) {
        error("String is violating the pattern: '" + StringUtil.convertLineSeparators(schema.getPattern()) + "'", propValue);
      }
    }
    // I think we are not gonna to support format, there are a couple of RFCs there to check upon..
    /*
    if (schema.getFormat() != null) {
      LOG.info("Unsupported property used: 'format'");
    }*/
  }

  private static boolean hasNumberChecks(JsonSchemaObject schema) {
    return schema.getMultipleOf() != null
           || schema.getExclusiveMinimumNumber() != null
           || schema.getExclusiveMaximumNumber() != null
           || schema.getMaximum() != null
           || schema.getMinimum() != null;
  }

  private void checkNumber(PsiElement propValue, JsonSchemaObject schema, JsonSchemaType schemaType) {
    Number value;
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(propValue, schema);
    assert walker != null;
    String valueText = walker.getNodeTextForValidation(propValue);
    if (JsonSchemaType._integer.equals(schemaType)) {
      try {
        value = Integer.valueOf(valueText);
      }
      catch (NumberFormatException e) {
        error("Integer value expected", propValue,
              JsonValidationError.FixableIssueKind.TypeMismatch,
              new JsonValidationError.TypeMismatchIssueData(new JsonSchemaType[]{schemaType}));
        return;
      }
    }
    else {
      try {
        value = Double.valueOf(valueText);
      }
      catch (NumberFormatException e) {
        if (!JsonSchemaType._string_number.equals(schemaType)) {
          error("Double value expected", propValue,
                JsonValidationError.FixableIssueKind.TypeMismatch,
                new JsonValidationError.TypeMismatchIssueData(new JsonSchemaType[]{schemaType}));
        }
        return;
      }
    }
    final Number multipleOf = schema.getMultipleOf();
    if (multipleOf != null) {
      final double leftOver = value.doubleValue() % multipleOf.doubleValue();
      if (leftOver > 0.000001) {
        final String multipleOfValue = String.valueOf(Math.abs(multipleOf.doubleValue() - multipleOf.intValue()) < 0.000001 ?
                                                      multipleOf.intValue() : multipleOf);
        error("Is not multiple of " + multipleOfValue, propValue);
        return;
      }
    }

    checkMinimum(schema, value, propValue, schemaType);
    checkMaximum(schema, value, propValue, schemaType);
  }

  private void checkMaximum(JsonSchemaObject schema, Number value, PsiElement propertyValue,
                            @NotNull JsonSchemaType propValueType) {

    Number exclusiveMaximumNumber = schema.getExclusiveMaximumNumber();
    if (exclusiveMaximumNumber != null) {
      if (JsonSchemaType._integer.equals(propValueType)) {
        final int intValue = exclusiveMaximumNumber.intValue();
        if (value.intValue() >= intValue) {
          error("Greater than an exclusive maximum " + intValue, propertyValue);
        }
      }
      else {
        final double doubleValue = exclusiveMaximumNumber.doubleValue();
        if (value.doubleValue() >= doubleValue) {
          error("Greater than an exclusive maximum " + exclusiveMaximumNumber, propertyValue);
        }
      }
    }
    Number maximum = schema.getMaximum();
    if (maximum == null) return;
    boolean isExclusive = Boolean.TRUE.equals(schema.isExclusiveMaximum());
    if (JsonSchemaType._integer.equals(propValueType)) {
      final int intValue = maximum.intValue();
      if (isExclusive) {
        if (value.intValue() >= intValue) {
          error("Greater than an exclusive maximum " + intValue, propertyValue);
        }
      }
      else {
        if (value.intValue() > intValue) {
          error("Greater than a maximum " + intValue, propertyValue);
        }
      }
    }
    else {
      final double doubleValue = maximum.doubleValue();
      if (isExclusive) {
        if (value.doubleValue() >= doubleValue) {
          error("Greater than an exclusive maximum " + maximum, propertyValue);
        }
      }
      else {
        if (value.doubleValue() > doubleValue) {
          error("Greater than a maximum " + maximum, propertyValue);
        }
      }
    }
  }

  private void checkMinimum(JsonSchemaObject schema, Number value, PsiElement propertyValue,
                            @NotNull JsonSchemaType schemaType) {
    // schema v6 - exclusiveMinimum is numeric now
    Number exclusiveMinimumNumber = schema.getExclusiveMinimumNumber();
    if (exclusiveMinimumNumber != null) {
      if (JsonSchemaType._integer.equals(schemaType)) {
        final int intValue = exclusiveMinimumNumber.intValue();
        if (value.intValue() <= intValue) {
          error("Less than an exclusive minimum" + intValue, propertyValue);
        }
      }
      else {
        final double doubleValue = exclusiveMinimumNumber.doubleValue();
        if (value.doubleValue() <= doubleValue) {
          error("Less than an exclusive minimum " + exclusiveMinimumNumber, propertyValue);
        }
      }
    }

    Number minimum = schema.getMinimum();
    if (minimum == null) return;
    boolean isExclusive = Boolean.TRUE.equals(schema.isExclusiveMinimum());
    if (JsonSchemaType._integer.equals(schemaType)) {
      final int intValue = minimum.intValue();
      if (isExclusive) {
        if (value.intValue() <= intValue) {
          error("Less than an exclusive minimum " + intValue, propertyValue);
        }
      }
      else {
        if (value.intValue() < intValue) {
          error("Less than a minimum " + intValue, propertyValue);
        }
      }
    }
    else {
      final double doubleValue = minimum.doubleValue();
      if (isExclusive) {
        if (value.doubleValue() <= doubleValue) {
          error("Less than an exclusive minimum " + minimum, propertyValue);
        }
      }
      else {
        if (value.doubleValue() < doubleValue) {
          error("Less than a minimum " + minimum, propertyValue);
        }
      }
    }
  }

  // returns the schema, selected for annotation
  private JsonSchemaObject processOneOf(@NotNull JsonValueAdapter value, List<JsonSchemaObject> oneOf) {
    final Map<PsiElement, JsonValidationError> errors = new HashMap<>();
    boolean wasTypeError = false;
    final List<JsonSchemaObject> correct = new SmartList<>();
    JsonSchemaObject current = null;
    for (JsonSchemaObject object : oneOf) {
      // skip it if something JS awaited, we do not process it currently
      if (object.isShouldValidateAgainstJSType()) continue;

      final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker();
      checker.checkByScheme(value, object);

      if (checker.isCorrect()) {
        current = object;
        errors.clear();
        correct.add(object);
      }
      else {
        if (errors.isEmpty() || wasTypeError && !checker.isHadTypeError() || errors.size() > checker.getErrors().size()) {
          wasTypeError = checker.isHadTypeError();
          current = object;
          errors.clear();
          errors.putAll(checker.getErrors());
        }
      }
    }
    if (correct.size() == 1) return correct.get(0);
    if (correct.size() > 0) {
      final JsonSchemaType type = JsonSchemaType.getType(value);
      if (type != null) {
        // also check maybe some currently not checked properties like format are different with schemes
        if (!schemesDifferWithNotCheckedProperties(correct)) {
          error("Validates to more than one variant", value.getDelegate());
        }
      }
    }
    else {
      if (!errors.isEmpty()) {
        for (Map.Entry<PsiElement, JsonValidationError> entry : errors.entrySet()) {
          error(entry.getKey(), entry.getValue());
        }
      }
    }
    return current;
  }

  private static boolean schemesDifferWithNotCheckedProperties(@NotNull final List<JsonSchemaObject> list) {
    return list.stream().anyMatch(s -> !StringUtil.isEmptyOrSpaces(s.getFormat()));
  }

  // returns the schema, selected for annotation
  private JsonSchemaObject processAnyOf(@NotNull JsonValueAdapter value, List<JsonSchemaObject> anyOf) {
    final Map<PsiElement, JsonValidationError> errors = new HashMap<>();
    JsonSchemaObject current = null;
    for (JsonSchemaObject object : anyOf) {
      final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker();
      checker.checkByScheme(value, object);
      if (checker.isCorrect()) {
        return object;
      }
      // maybe we still find the correct schema - continue to iterate
      if (errors.isEmpty() && !checker.isHadTypeError()) {
        current = object;
        errors.clear();
        errors.putAll(checker.getErrors());
      }
    }
    if (!errors.isEmpty()) {
      for (Map.Entry<PsiElement, JsonValidationError> entry : errors.entrySet()) {
        error(entry.getKey(), entry.getValue());
      }
    }
    return current;
  }

  public boolean isCorrect() {
    return myErrors.isEmpty();
  }
}
