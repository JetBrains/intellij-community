// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonBundle;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonContainer;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
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
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder.doSingleStep;

/**
 * @author Irina.Chernushina on 4/25/2017.
 */
class JsonSchemaAnnotatorChecker {
  private static final Set<JsonSchemaType> PRIMITIVE_TYPES =
    ContainerUtil.set(JsonSchemaType._integer, JsonSchemaType._number, JsonSchemaType._boolean, JsonSchemaType._string, JsonSchemaType._null);
  private final Map<PsiElement, JsonValidationError> myErrors;
  private final JsonComplianceCheckerOptions myOptions;
  private boolean myHadTypeError;
  private static final String ENUM_MISMATCH_PREFIX = "Value should be one of: ";

  protected JsonSchemaAnnotatorChecker(JsonComplianceCheckerOptions options) {
    myOptions = options;
    myErrors = new HashMap<>();
  }

  public Map<PsiElement, JsonValidationError> getErrors() {
    return myErrors;
  }

  public boolean isHadTypeError() {
    return myHadTypeError;
  }

  public static JsonSchemaAnnotatorChecker checkByMatchResult(@NotNull JsonValueAdapter elementToCheck,
                                                              @NotNull final MatchResult result,
                                                              @NotNull JsonComplianceCheckerOptions options) {
    final List<JsonSchemaAnnotatorChecker> checkers = new ArrayList<>();
    if (result.myExcludingSchemas.isEmpty() && result.mySchemas.size() == 1) {
      final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(options);
      checker.checkByScheme(elementToCheck, result.mySchemas.iterator().next());
      checkers.add(checker);
    }
    else {
      if (!result.mySchemas.isEmpty()) {
        checkers.add(processSchemasVariants(result.mySchemas, elementToCheck, false, options).getSecond());
      }
      if (!result.myExcludingSchemas.isEmpty()) {
        // we can have several oneOf groups, each about, for instance, a part of properties
        // - then we should allow properties from neighbour schemas (even if additionalProperties=false)
        final List<JsonSchemaAnnotatorChecker> list =
          ContainerUtil.map(result.myExcludingSchemas, group -> processSchemasVariants(group, elementToCheck, true, options).getSecond());
        checkers.add(mergeErrors(list, options, result.myExcludingSchemas));
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
                                                        @NotNull JsonComplianceCheckerOptions options,
                                                        List<Collection<? extends JsonSchemaObject>> excludingSchemas) {
    final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(options);

    for (JsonSchemaAnnotatorChecker ch: list) {
      for (Map.Entry<PsiElement, JsonValidationError> element: ch.myErrors.entrySet()) {
        JsonValidationError error = element.getValue();
        if (error.getFixableIssueKind() == JsonValidationError.FixableIssueKind.ProhibitedProperty) {
          String propertyName = ((JsonValidationError.ProhibitedPropertyIssueData)error.getIssueData()).propertyName;
          boolean skip = false;
          for (Collection<? extends JsonSchemaObject> objects : excludingSchemas) {
            Set<String> keys = objects.stream().map(o -> o.getProperties().keySet()).flatMap(Set::stream).collect(Collectors.toSet());
            if (keys.contains(propertyName)) skip = true;
          }
          if (skip) continue;
        }
        checker.myErrors.put(element.getKey(), error);
      }
    }
    return checker;
  }

  private void error(final String error, final PsiElement holder,
                     JsonErrorPriority priority) {
    error(error, holder, JsonValidationError.FixableIssueKind.None, null, priority);
  }

  private void error(final PsiElement newHolder, JsonValidationError error) {
    error(error.getMessage(), newHolder, error.getFixableIssueKind(), error.getIssueData(), error.getPriority());
  }

  private void error(final String error, final PsiElement holder,
                     JsonValidationError.FixableIssueKind fixableIssueKind,
                     JsonValidationError.IssueData data,
                     JsonErrorPriority priority) {
    if (myErrors.containsKey(holder)) return;
    myErrors.put(holder, new JsonValidationError(error, fixableIssueKind, data, priority));
  }

  private void typeError(final @NotNull PsiElement value, final @NotNull JsonSchemaType... allowedTypes) {
    if (allowedTypes.length > 0) {
      if (allowedTypes.length == 1) {
        error(String.format("Type is not allowed. Expected: %s.", allowedTypes[0].getName()), value,
              JsonValidationError.FixableIssueKind.ProhibitedType,
              new JsonValidationError.TypeMismatchIssueData(allowedTypes),
              JsonErrorPriority.TYPE_MISMATCH);
      } else {
        final String typesText = Arrays.stream(allowedTypes)
                                       .map(JsonSchemaType::getName)
                                       .distinct()
                                       .sorted(Comparator.naturalOrder())
                                       .collect(Collectors.joining(", "));
        error(String.format("Type is not allowed. Expected one of: %s.", typesText), value,
              JsonValidationError.FixableIssueKind.ProhibitedType,
              new JsonValidationError.TypeMismatchIssueData(allowedTypes),
              JsonErrorPriority.TYPE_MISMATCH);
      }
    } else {
      error("Type is not allowed", value, JsonErrorPriority.TYPE_MISMATCH);
    }
    myHadTypeError = true;
  }

  public void checkByScheme(@NotNull JsonValueAdapter value, @NotNull JsonSchemaObject schema) {
    final JsonSchemaType type = JsonSchemaType.getType(value);
    checkForEnum(value.getDelegate(), schema);
    boolean checkedNumber = false;
    boolean checkedString = false;
    boolean checkedArray = false;
    boolean checkedObject = false;
    if (type != null) {
      JsonSchemaType schemaType = getMatchingSchemaType(schema, type);
      if (schemaType != null && !schemaType.equals(type)) {
        typeError(value.getDelegate(), schemaType);
      }
      else {
        if (JsonSchemaType._string_number.equals(type)) {
          checkNumber(value.getDelegate(), schema, type);
          checkedNumber = true;
          checkString(value.getDelegate(), schema);
          checkedString = true;
        }
        else if (JsonSchemaType._number.equals(type) || JsonSchemaType._integer.equals(type)) {
          checkNumber(value.getDelegate(), schema, type);
          checkedNumber = true;
        }
        else if (JsonSchemaType._string.equals(type)) {
          checkString(value.getDelegate(), schema);
          checkedString = true;
        }
        else if (JsonSchemaType._array.equals(type)) {
          checkArray(value, schema);
          checkedArray = true;
        }
        else if (JsonSchemaType._object.equals(type)) {
          checkObject(value, schema);
          checkedObject = true;
        }
      }
    }

    if ((!myHadTypeError || myErrors.isEmpty()) && !value.isShouldBeIgnored()) {
      PsiElement delegate = value.getDelegate();
      if (!checkedNumber && schema.hasNumericChecks() && value.isNumberLiteral()) {
        checkNumber(delegate, schema, JsonSchemaType._number);
      }
      if (!checkedString && schema.hasStringChecks() && value.isStringLiteral()) {
        checkString(delegate, schema);
        checkedString = true;
      }
      if (!checkedArray && schema.hasArrayChecks() && value.isArray()) {
        checkArray(value, schema);
        checkedArray = true;
      }
      if (hasMinMaxLengthChecks(schema)) {
        if (value.isStringLiteral()) {
          if (!checkedString) {
            checkString(delegate, schema);
          }
        }
        else if (value.isArray()) {
          if (!checkedArray) {
            checkArray(value, schema);
          }
        }
      }
      if (!checkedObject && schema.hasObjectChecks() && value.isObject()) {
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

      final JsonSchemaAnnotatorChecker checker = checkByMatchResult(value, result, myOptions);
      if (checker == null || checker.isCorrect()) error("Validates against 'not' schema", value.getDelegate(), JsonErrorPriority.NOT_SCHEMA);
    }

    if (schema.getIf() != null) {
      MatchResult result = new JsonSchemaResolver(schema.getIf()).detailedResolve();
      if (result.mySchemas.isEmpty() && result.myExcludingSchemas.isEmpty()) return;

      final JsonSchemaAnnotatorChecker checker = checkByMatchResult(value, result, myOptions);
      if (checker != null) {
        if (checker.isCorrect()) {
          JsonSchemaObject then = schema.getThen();
          if (then == null) {
            error("Validates against 'if' branch but no 'then' branch is present", value.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
          }
          else {
            checkObjectBySchemaRecordErrors(then, value);
          }
        }
        else {
          JsonSchemaObject schemaElse = schema.getElse();
          if (schemaElse == null) {
            error("Validates counter 'if' branch but no 'else' branch is present", value.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
          }
          else {
            checkObjectBySchemaRecordErrors(schemaElse, value);
          }
        }
      }
    }
  }

  private void checkObjectBySchemaRecordErrors(@NotNull JsonSchemaObject schema, @NotNull JsonValueAdapter object) {
    final JsonSchemaAnnotatorChecker checker = checkByMatchResult(object, new JsonSchemaResolver(schema).detailedResolve(), myOptions);
    if (checker != null) {
      myHadTypeError = checker.isHadTypeError();
      myErrors.putAll(checker.getErrors());
    }
  }

  private void checkObject(@NotNull JsonValueAdapter value, @NotNull JsonSchemaObject schema) {
    final JsonObjectValueAdapter object = value.getAsObject();
    if (object == null) return;

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

      final JsonPointerPosition step = JsonPointerPosition.createSingleProperty(name);
      final Pair<ThreeState, JsonSchemaObject> pair = doSingleStep(step, schema, true);
      if (ThreeState.NO.equals(pair.getFirst()) && !set.contains(name)) {
        error(JsonBundle.message("json.schema.annotation.not.allowed.property", name), property.getDelegate(),
              JsonValidationError.FixableIssueKind.ProhibitedProperty,
              new JsonValidationError.ProhibitedPropertyIssueData(name), JsonErrorPriority.LOW_PRIORITY);
      }
      else if (ThreeState.UNSURE.equals(pair.getFirst())) {
        for (JsonValueAdapter propertyValue : property.getValues()) {
          checkObjectBySchemaRecordErrors(pair.getSecond(), propertyValue);
        }
      }
      set.add(name);
    }

    if (object.shouldCheckIntegralRequirements()) {
      final Set<String> required = schema.getRequired();
      if (required != null) {
        HashSet<String> requiredNames = ContainerUtil.newHashSet(required);
        requiredNames.removeAll(set);
        if (!requiredNames.isEmpty()) {
          JsonValidationError.MissingMultiplePropsIssueData data = createMissingPropertiesData(schema, requiredNames);
          error("Missing required " + data.getMessage(false), value.getDelegate(), JsonValidationError.FixableIssueKind.MissingProperty, data,
                JsonErrorPriority.MISSING_PROPS);
        }
      }
      if (schema.getMinProperties() != null && propertyList.size() < schema.getMinProperties()) {
        error("Number of properties is less than " + schema.getMinProperties(), value.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
      }
      if (schema.getMaxProperties() != null && propertyList.size() > schema.getMaxProperties()) {
        error("Number of properties is greater than " + schema.getMaxProperties(), value.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
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
                    data, JsonErrorPriority.MISSING_PROPS);
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

  @Nullable
  private static Object getDefaultValueFromEnum(@NotNull JsonSchemaObject propertySchema, @NotNull Ref<Integer> enumCount) {
    List<Object> enumValues = propertySchema.getEnum();
    if (enumValues != null) {
      enumCount.set(enumValues.size());
      if (enumValues.size() == 1) {
        Object defaultObject = enumValues.get(0);
        return defaultObject instanceof String ? StringUtil.unquoteString((String)defaultObject) : defaultObject;
      }
    }
    return null;
  }

  @NotNull
  private static JsonValidationError.MissingMultiplePropsIssueData createMissingPropertiesData(@NotNull JsonSchemaObject schema,
                                                                                               HashSet<String> requiredNames) {
    List<JsonValidationError.MissingPropertyIssueData> allProps = ContainerUtil.newArrayList();
    for (String req: requiredNames) {
      JsonSchemaObject propertySchema = resolvePropertySchema(schema, req);
      Object defaultValue = propertySchema == null ? null : propertySchema.getDefault();
      Ref<Integer> enumCount = Ref.create(0);

      JsonSchemaType type = null;

      if (propertySchema != null) {
        MatchResult result = null;
        Object valueFromEnum = getDefaultValueFromEnum(propertySchema, enumCount);
        if (valueFromEnum != null) {
          defaultValue = valueFromEnum;
        }
        else {
          result = new JsonSchemaResolver(propertySchema).detailedResolve();
          if (result.mySchemas.size() == 1) {
            valueFromEnum = getDefaultValueFromEnum(result.mySchemas.get(0), enumCount);
            if (valueFromEnum != null) {
              defaultValue = valueFromEnum;
            }
          }
        }
        type = propertySchema.getType();
        if (type == null) {
          if (result == null) {
            result = new JsonSchemaResolver(propertySchema).detailedResolve();
          }
          if (result.mySchemas.size() == 1) {
            type = result.mySchemas.get(0).getType();
          }
        }
      }
      allProps.add(new JsonValidationError.MissingPropertyIssueData(req,
                                                                    type,
                                                                    defaultValue,
                                                                    enumCount.get()));
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

    final JsonPointerPosition position = JsonOriginalPsiWalker.INSTANCE.findPosition(object, true);
    if (position == null) return;
    // !! not root schema, because we validate the schema written in the file itself
    final MatchResult result = new JsonSchemaResolver(schemaObject, false, position).detailedResolve();
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
        error(StringUtil.convertLineSeparators(patternError), pattern.getValue(), JsonErrorPriority.LOW_PRIORITY);
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
        error(StringUtil.convertLineSeparators(entry.getValue()), ((JsonProperty)parent).getNameElement(), JsonErrorPriority.LOW_PRIORITY);
      }
    }
  }

  private static boolean checkEnumValue(@NotNull Object object,
                                        @NotNull JsonLikePsiWalker walker,
                                        @Nullable JsonValueAdapter adapter,
                                        @NotNull String text,
                                        @NotNull BiFunction<String, String, Boolean> stringEq) {
    if (adapter != null && !adapter.shouldCheckAsValue()) return true;
    if (object instanceof EnumArrayValueWrapper) {
      if (adapter instanceof JsonArrayValueAdapter) {
        List<JsonValueAdapter> elements = ((JsonArrayValueAdapter)adapter).getElements();
        Object[] values = ((EnumArrayValueWrapper)object).getValues();
        if (elements.size() == values.length) {
          for (int i = 0; i < values.length; i++) {
            if (!checkEnumValue(values[i], walker, elements.get(i), walker.getNodeTextForValidation(elements.get(i).getDelegate()), stringEq)) return false;
          }
          return true;
        }
      }
    }
    else if (object instanceof EnumObjectValueWrapper) {
      if (adapter instanceof JsonObjectValueAdapter) {
        List<JsonPropertyAdapter> props = ((JsonObjectValueAdapter)adapter).getPropertyList();
        Map<String, Object> values = ((EnumObjectValueWrapper)object).getValues();
        if (props.size() == values.size()) {
          for (JsonPropertyAdapter prop : props) {
            if (!values.containsKey(prop.getName())) return false;
            for (JsonValueAdapter value : prop.getValues()) {
              if (!checkEnumValue(values.get(prop.getName()), walker, value, walker.getNodeTextForValidation(value.getDelegate()), stringEq)) return false;
            }
          }

          return true;
        }
      }
    }
    else {
      if (!walker.allowsSingleQuotes()) {
        if (stringEq.apply(object.toString(), text)) return true;
      }
      else {
        if (equalsIgnoreQuotes(object.toString(), text, walker.requiresValueQuotes(), stringEq)) return true;
      }
    }

    return false;
  }

  private void checkForEnum(PsiElement value, JsonSchemaObject schema) {
    List<Object> enumItems = schema.getEnum();
    if (enumItems == null) return;
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(value, schema);
    if (walker == null) return;
    final String text = StringUtil.notNullize(walker.getNodeTextForValidation(value));
    BiFunction<String, String, Boolean> eq = myOptions.isCaseInsensitiveEnumCheck() ? String::equalsIgnoreCase : String::equals;
    for (Object object : enumItems) {
      if (checkEnumValue(object, walker, walker.createValueAdapter(value), text, eq)) return;
    }
    error(ENUM_MISMATCH_PREFIX + StringUtil.join(enumItems, o -> o.toString(), ", "), value,
          JsonValidationError.FixableIssueKind.NonEnumValue, null, JsonErrorPriority.MEDIUM_PRIORITY);
  }

  private static boolean equalsIgnoreQuotes(@NotNull final String s1,
                                            @NotNull final String s2,
                                            boolean requireQuotedValues,
                                            BiFunction<String, String, Boolean> eq) {
    final boolean quoted1 = StringUtil.isQuotedString(s1);
    final boolean quoted2 = StringUtil.isQuotedString(s2);
    if (requireQuotedValues && quoted1 != quoted2) return false;
    if (requireQuotedValues && !quoted1) return eq.apply(s1, s2);
    return eq.apply(StringUtil.unquoteString(s1), StringUtil.unquoteString(s2));
  }

  private void checkArray(JsonValueAdapter value, JsonSchemaObject schema) {
    final JsonArrayValueAdapter asArray = value.getAsArray();
    if (asArray == null) return;
    final List<JsonValueAdapter> elements = asArray.getElements();
    if (schema.getMinLength() != null && elements.size() < schema.getMinLength()) {
      error("Array is shorter than " + schema.getMinLength(), value.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
      return;
    }
    checkArrayItems(value, elements, schema);
  }

  @NotNull
  private static Pair<JsonSchemaObject, JsonSchemaAnnotatorChecker> processSchemasVariants(
    @NotNull final Collection<? extends JsonSchemaObject> collection,
    @NotNull final JsonValueAdapter value, boolean isOneOf, JsonComplianceCheckerOptions options) {

    final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(options);
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
        final Set<JsonSchemaType> variants = schema.getTypeVariants();
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
      Set<JsonSchemaType> matchTypes = schema.getTypeVariants();
      if (matchTypes.contains(input)) {
        return input;
      }
      if (JsonSchemaType._integer.equals(input) && matchTypes.contains(JsonSchemaType._number)) {
        return input;
      }
      if (JsonSchemaType._string_number.equals(input) &&
          (matchTypes.contains(JsonSchemaType._number)
           || matchTypes.contains(JsonSchemaType._integer)
           || matchTypes.contains(JsonSchemaType._string))) {
        return input;
      }
      //nothing matches, lets return one of the list so that other heuristics does not match
      return matchTypes.iterator().next();
    }
    if (!schema.getProperties().isEmpty() && JsonSchemaType._object.equals(input)) return JsonSchemaType._object;
    return null;
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
            if (!item.shouldCheckAsValue()) continue;
            error("Item is not unique", item.getDelegate(), JsonErrorPriority.TYPE_MISMATCH);
          }
        }
      }
    }
    if (schema.getContainsSchema() != null) {
      boolean match = false;
      for (JsonValueAdapter item: list) {
        final JsonSchemaAnnotatorChecker checker = checkByMatchResult(item, new JsonSchemaResolver(schema.getContainsSchema()).detailedResolve(), myOptions);
        if (checker == null || checker.myErrors.size() == 0 && !checker.myHadTypeError) {
          match = true;
          break;
        }
      }
      if (!match) {
        error("No match for 'contains' rule", array.getDelegate(), JsonErrorPriority.MEDIUM_PRIORITY);
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
            error("Additional items are not allowed", arrayValue.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
          }
          else if (schema.getAdditionalItemsSchema() != null) {
            checkObjectBySchemaRecordErrors(schema.getAdditionalItemsSchema(), arrayValue);
          }
        }
      }
    }
    if (schema.getMinItems() != null && list.size() < schema.getMinItems()) {
      error("Array is shorter than " + schema.getMinItems(), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
    }
    if (schema.getMaxItems() != null && list.size() > schema.getMaxItems()) {
      error("Array is longer than " + schema.getMaxItems(), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
    }
  }

  private static boolean hasMinMaxLengthChecks(JsonSchemaObject schema) {
    return schema.getMinLength() != null || schema.getMaxLength() != null;
  }

  private void checkString(PsiElement propValue, JsonSchemaObject schema) {
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(propValue, schema);
    assert walker != null;
    JsonValueAdapter adapter = walker.createValueAdapter(propValue);
    if (adapter != null && !adapter.shouldCheckAsValue()) return;
    final String value = StringUtil.unquoteString(walker.getNodeTextForValidation(propValue));
    if (schema.getMinLength() != null) {
      if (value.length() < schema.getMinLength()) {
        error("String is shorter than " + schema.getMinLength(), propValue, JsonErrorPriority.LOW_PRIORITY);
        return;
      }
    }
    if (schema.getMaxLength() != null) {
      if (value.length() > schema.getMaxLength()) {
        error("String is longer than " + schema.getMaxLength(), propValue, JsonErrorPriority.LOW_PRIORITY);
        return;
      }
    }
    if (schema.getPattern() != null) {
      if (schema.getPatternError() != null) {
        error("Can not check string by pattern because of error: " + StringUtil.convertLineSeparators(schema.getPatternError()),
              propValue, JsonErrorPriority.LOW_PRIORITY);
      }
      if (!schema.checkByPattern(value)) {
        error("String is violating the pattern: '" + StringUtil.convertLineSeparators(schema.getPattern()) + "'", propValue, JsonErrorPriority.LOW_PRIORITY);
      }
    }
    // I think we are not gonna to support format, there are a couple of RFCs there to check upon..
    /*
    if (schema.getFormat() != null) {
      LOG.info("Unsupported property used: 'format'");
    }*/
  }

  private void checkNumber(PsiElement propValue, JsonSchemaObject schema, JsonSchemaType schemaType) {
    Number value;
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(propValue, schema);
    assert walker != null;
    JsonValueAdapter adapter = walker.createValueAdapter(propValue);
    if (adapter != null && !adapter.shouldCheckAsValue()) return;
    String valueText = walker.getNodeTextForValidation(propValue);
    if (JsonSchemaType._integer.equals(schemaType)) {
      try {
        value = Integer.valueOf(valueText);
      }
      catch (NumberFormatException e) {
        error("Integer value expected", propValue,
              JsonValidationError.FixableIssueKind.TypeMismatch,
              new JsonValidationError.TypeMismatchIssueData(new JsonSchemaType[]{schemaType}), JsonErrorPriority.TYPE_MISMATCH);
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
                new JsonValidationError.TypeMismatchIssueData(new JsonSchemaType[]{schemaType}), JsonErrorPriority.TYPE_MISMATCH);
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
        error("Is not multiple of " + multipleOfValue, propValue, JsonErrorPriority.LOW_PRIORITY);
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
          error("Greater than an exclusive maximum " + intValue, propertyValue, JsonErrorPriority.LOW_PRIORITY);
        }
      }
      else {
        final double doubleValue = exclusiveMaximumNumber.doubleValue();
        if (value.doubleValue() >= doubleValue) {
          error("Greater than an exclusive maximum " + exclusiveMaximumNumber, propertyValue, JsonErrorPriority.LOW_PRIORITY);
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
          error("Greater than an exclusive maximum " + intValue, propertyValue, JsonErrorPriority.LOW_PRIORITY);
        }
      }
      else {
        if (value.intValue() > intValue) {
          error("Greater than a maximum " + intValue, propertyValue, JsonErrorPriority.LOW_PRIORITY);
        }
      }
    }
    else {
      final double doubleValue = maximum.doubleValue();
      if (isExclusive) {
        if (value.doubleValue() >= doubleValue) {
          error("Greater than an exclusive maximum " + maximum, propertyValue, JsonErrorPriority.LOW_PRIORITY);
        }
      }
      else {
        if (value.doubleValue() > doubleValue) {
          error("Greater than a maximum " + maximum, propertyValue, JsonErrorPriority.LOW_PRIORITY);
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
          error("Less than an exclusive minimum" + intValue, propertyValue, JsonErrorPriority.LOW_PRIORITY);
        }
      }
      else {
        final double doubleValue = exclusiveMinimumNumber.doubleValue();
        if (value.doubleValue() <= doubleValue) {
          error("Less than an exclusive minimum " + exclusiveMinimumNumber, propertyValue, JsonErrorPriority.LOW_PRIORITY);
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
          error("Less than an exclusive minimum " + intValue, propertyValue, JsonErrorPriority.LOW_PRIORITY);
        }
      }
      else {
        if (value.intValue() < intValue) {
          error("Less than a minimum " + intValue, propertyValue, JsonErrorPriority.LOW_PRIORITY);
        }
      }
    }
    else {
      final double doubleValue = minimum.doubleValue();
      if (isExclusive) {
        if (value.doubleValue() <= doubleValue) {
          error("Less than an exclusive minimum " + minimum, propertyValue, JsonErrorPriority.LOW_PRIORITY);
        }
      }
      else {
        if (value.doubleValue() < doubleValue) {
          error("Less than a minimum " + minimum, propertyValue, JsonErrorPriority.LOW_PRIORITY);
        }
      }
    }
  }

  // returns the schema, selected for annotation
  private JsonSchemaObject processOneOf(@NotNull JsonValueAdapter value, List<JsonSchemaObject> oneOf) {
    final List<JsonSchemaAnnotatorChecker> candidateErroneousCheckers = ContainerUtil.newArrayList();
    final List<JsonSchemaObject> candidateErroneousSchemas = ContainerUtil.newArrayList();
    final List<JsonSchemaObject> correct = new SmartList<>();
    for (JsonSchemaObject object : oneOf) {
      // skip it if something JS awaited, we do not process it currently
      if (object.isShouldValidateAgainstJSType()) continue;

      final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(myOptions);
      checker.checkByScheme(value, object);

      if (checker.isCorrect()) {
        candidateErroneousCheckers.clear();
        candidateErroneousSchemas.clear();
        correct.add(object);
      }
      else {
        candidateErroneousCheckers.add(checker);
        candidateErroneousSchemas.add(object);
      }
    }
    if (correct.size() == 1) return correct.get(0);
    if (correct.size() > 0) {
      final JsonSchemaType type = JsonSchemaType.getType(value);
      if (type != null) {
        // also check maybe some currently not checked properties like format are different with schemes
        // todo note that JsonSchemaObject#equals is broken by design, so normally it shouldn't be used until rewritten
        //  but for now we use it here to avoid similar schemas being marked as duplicates
        if (ContainerUtil.newHashSet(correct).size() > 1 && !schemesDifferWithNotCheckedProperties(correct)) {
          error("Validates to more than one variant", value.getDelegate(), JsonErrorPriority.MEDIUM_PRIORITY);
        }
      }
      return ContainerUtil.getLastItem(correct);
    }

    return showErrorsAndGetLeastErroneous(candidateErroneousCheckers, candidateErroneousSchemas, true);
  }

  private static boolean schemesDifferWithNotCheckedProperties(@NotNull final List<JsonSchemaObject> list) {
    return list.stream().anyMatch(s -> !StringUtil.isEmptyOrSpaces(s.getFormat()));
  }

  private enum AverageFailureAmount {
    Light,
    MissingItems,
    Medium,
    Hard,
    NotSchema
  }

  @NotNull
  private static AverageFailureAmount getAverageFailureAmount(@NotNull JsonSchemaAnnotatorChecker checker) {
    int lowPriorityCount = 0;
    boolean hasMedium = false;
    boolean hasMissing = false;
    boolean hasHard = false;
    Collection<JsonValidationError> values = checker.getErrors().values();
    for (JsonValidationError value: values) {
      switch (value.getPriority()) {
        case LOW_PRIORITY:
          lowPriorityCount++;
          break;
        case MISSING_PROPS:
          hasMissing = true;
          break;
        case MEDIUM_PRIORITY:
          hasMedium = true;
          break;
        case TYPE_MISMATCH:
          hasHard = true;
          break;
        case NOT_SCHEMA:
          return AverageFailureAmount.NotSchema;
      }
    }

    if (hasHard) {
      return AverageFailureAmount.Hard;
    }

    // missing props should win against other conditions
    if (hasMissing) {
      return AverageFailureAmount.MissingItems;
    }

    if (hasMedium) {
      return AverageFailureAmount.Medium;
    }

    return lowPriorityCount <= 3 ? AverageFailureAmount.Light : AverageFailureAmount.Medium;
  }

  // returns the schema, selected for annotation
  private JsonSchemaObject processAnyOf(@NotNull JsonValueAdapter value, List<JsonSchemaObject> anyOf) {
    final List<JsonSchemaAnnotatorChecker> candidateErroneousCheckers = ContainerUtil.newArrayList();
    final List<JsonSchemaObject> candidateErroneousSchemas = ContainerUtil.newArrayList();

    for (JsonSchemaObject object : anyOf) {
      final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(myOptions);
      checker.checkByScheme(value, object);
      if (checker.isCorrect()) {
        return object;
      }
      // maybe we still find the correct schema - continue to iterate
      candidateErroneousCheckers.add(checker);
      candidateErroneousSchemas.add(object);
    }

    return showErrorsAndGetLeastErroneous(candidateErroneousCheckers, candidateErroneousSchemas, false);
  }

  /**
   * Filters schema validation results to get the result with the "minimal" amount of errors.
   * This is needed in case of oneOf or anyOf conditions, when there exist no match.
   * I.e., when we have multiple schema candidates, but none is applicable.
   * In this case we need to show the most "suitable" error messages
   *   - by detecting the most "likely" schema corresponding to the current entity
   */
  @Nullable
  private JsonSchemaObject showErrorsAndGetLeastErroneous(@NotNull List<JsonSchemaAnnotatorChecker> candidateErroneousCheckers,
                                                          @NotNull List<JsonSchemaObject> candidateErroneousSchemas,
                                                          boolean isOneOf) {
    JsonSchemaObject current = null;
    JsonSchemaObject currentWithMinAverage = null;
    Optional<AverageFailureAmount> minAverage = candidateErroneousCheckers.stream()
                                                                          .map(c -> getAverageFailureAmount(c))
                                                                          .min(Comparator.comparingInt(c -> c.ordinal()));
    int min = minAverage.orElse(AverageFailureAmount.Hard).ordinal();

    int minErrorCount = candidateErroneousCheckers.stream().map(c -> c.getErrors().size()).min(Integer::compareTo).orElse(Integer.MAX_VALUE);

    MultiMap<PsiElement, JsonValidationError> errorsWithMinAverage = MultiMap.create();
    MultiMap<PsiElement, JsonValidationError> allErrors = MultiMap.create();
    for (int i = 0; i < candidateErroneousCheckers.size(); i++) {
      JsonSchemaAnnotatorChecker checker = candidateErroneousCheckers.get(i);
      final boolean isMoreThanMinErrors = checker.getErrors().size() > minErrorCount;
      final boolean isMoreThanAverage = getAverageFailureAmount(checker).ordinal() > min;
      if (!isMoreThanMinErrors) {
        if (isMoreThanAverage) {
          currentWithMinAverage = candidateErroneousSchemas.get(i);
        }
        else {
          current = candidateErroneousSchemas.get(i);
        }

        for (Map.Entry<PsiElement, JsonValidationError> entry: checker.getErrors().entrySet()) {
          (isMoreThanAverage ? errorsWithMinAverage : allErrors).putValue(entry.getKey(), entry.getValue());
        }
      }
    }

    if (allErrors.isEmpty()) allErrors = errorsWithMinAverage;

    for (Map.Entry<PsiElement, Collection<JsonValidationError>> entry : allErrors.entrySet()) {
      Collection<JsonValidationError> value = entry.getValue();
      if (value.size() == 0) continue;
      if (value.size() == 1) {
        error(entry.getKey(), value.iterator().next());
        continue;
      }
      JsonValidationError error = tryMergeErrors(value, isOneOf);
      if (error != null) {
        error(entry.getKey(), error);
      }
      else {
        for (JsonValidationError validationError : value) {
          error(entry.getKey(), validationError);
        }
      }
    }

    if (current == null) {
      current = currentWithMinAverage;
    }
    if (current == null) {
      current = ContainerUtil.getLastItem(candidateErroneousSchemas);
    }

    return current;
  }

  @Nullable
  private static JsonValidationError tryMergeErrors(@NotNull Collection<JsonValidationError> errors, boolean isOneOf) {
    JsonValidationError.FixableIssueKind commonIssueKind = null;
    for (JsonValidationError error : errors) {
      JsonValidationError.FixableIssueKind currentIssueKind = error.getFixableIssueKind();
      if (currentIssueKind == JsonValidationError.FixableIssueKind.None) return null;
      else if (commonIssueKind == null) commonIssueKind = currentIssueKind;
      else if (currentIssueKind != commonIssueKind) return null;
    }

    if (commonIssueKind == JsonValidationError.FixableIssueKind.NonEnumValue) {
      return new JsonValidationError(ENUM_MISMATCH_PREFIX
                                     + errors
                                       .stream()
                                       .map(e -> StringUtil.trimStart(e.getMessage(), ENUM_MISMATCH_PREFIX))
                                       .map(e -> StringUtil.split(e, ", "))
                                       .flatMap(e -> e.stream())
                                       .distinct()
                                       .collect(Collectors.joining(", ")), commonIssueKind, null, errors.iterator().next().getPriority());
    }

    if (commonIssueKind == JsonValidationError.FixableIssueKind.MissingProperty) {
      String prefix = isOneOf ? "One of the following property sets is required: " : "Should have at least one of the following property sets: ";
      return new JsonValidationError(prefix +
                                     errors.stream().map(e -> (JsonValidationError.MissingMultiplePropsIssueData)e.getIssueData())
                                    .map(d -> d.getMessage(false)).collect(Collectors.joining(", or ")),
                                     isOneOf ? JsonValidationError.FixableIssueKind.MissingOneOfProperty : JsonValidationError.FixableIssueKind.MissingAnyOfProperty,
                                     new JsonValidationError.MissingOneOfPropsIssueData(
                                       ContainerUtil.map(errors, e -> (JsonValidationError.MissingMultiplePropsIssueData)e.getIssueData())), errors.iterator().next().getPriority());
    }

    if (commonIssueKind == JsonValidationError.FixableIssueKind.ProhibitedType) {
      final Set<JsonSchemaType> allTypes = errors.stream().map(e -> (JsonValidationError.TypeMismatchIssueData)e.getIssueData())
        .flatMap(d -> Arrays.stream(d.expectedTypes)).collect(Collectors.toSet());

      if (allTypes.size() == 1) return errors.iterator().next();

      String commonTypeMessage = "Type is not allowed. Expected one of: " + allTypes.stream().map(t -> t.getDescription()).sorted().collect(Collectors.joining(", ")) + ".";
      return new JsonValidationError(commonTypeMessage, JsonValidationError.FixableIssueKind.TypeMismatch,
                                     new JsonValidationError.TypeMismatchIssueData(ContainerUtil.toArray(allTypes, JsonSchemaType[]::new)),
                                     errors.iterator().next().getPriority());
    }

    return null;
  }

  public boolean isCorrect() {
    return myErrors.isEmpty();
  }
}
