// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import com.intellij.json.JsonBundle;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.EditDistance;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.impl.*;
import kotlin.collections.CollectionsKt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder.doSingleStep;

public final class ObjectValidation implements JsonSchemaValidation {
  public static final ObjectValidation INSTANCE = new ObjectValidation();

  @Override
  public boolean validate(@NotNull JsonValueAdapter propValue,
                          @NotNull JsonSchemaObject schema,
                          @Nullable JsonSchemaType schemaType,
                          @NotNull JsonValidationHost consumer,
                          @NotNull JsonComplianceCheckerOptions options) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.ObjectValidation);
    return checkObject(propValue, schema, consumer, options);
  }

  private static final int MIN_LENGTH_TO_FIX_TYPOS = 3;

  private static boolean checkObject(@NotNull JsonValueAdapter value,
                                     @NotNull JsonSchemaObject schema,
                                     JsonValidationHost consumer,
                                     JsonComplianceCheckerOptions options) {
    final JsonObjectValueAdapter object = value.getAsObject();
    if (object == null) return true;

    var isValid = true;

    final List<JsonPropertyAdapter> propertyList = object.getPropertyList();
    final Set<String> set = new HashSet<>();
    for (JsonPropertyAdapter property : propertyList) {
      final String name = StringUtil.notNullize(property.getName());
      JsonSchemaObject propertyNamesSchema = schema.getPropertyNamesSchema();
      JsonValueAdapter nameValueAdapter = property.getNameValueAdapter();
      if (propertyNamesSchema != null) {
        if (nameValueAdapter != null) {
          JsonValidationHost checker = consumer.checkByMatchResult(nameValueAdapter, consumer.resolve(propertyNamesSchema,
                                                                                                      nameValueAdapter), options);
          if (checker != null) {
            consumer.addErrorsFrom(checker);
            isValid = false;
            if (options.shouldStopValidationAfterAnyErrorFound()) return false;
          }
        }
      }

      final JsonPointerPosition step = JsonPointerPosition.createSingleProperty(name);
      final Pair<ThreeState, JsonSchemaObject> pair = doSingleStep(step, schema);
      if (ThreeState.NO.equals(pair.getFirst()) && !set.contains(name)) {
        Iterator<String> propertyNamesIterator = schema.getPropertyNames();
        List<@NlsSafe String> typoCandidates = CollectionsKt.filter(
          iteratorToList(propertyNamesIterator),
          s -> EditDistance.optimalAlignment(s, name, false, 1) <= 1
        );
        consumer.error(JsonBundle.message(
                         name.length() < MIN_LENGTH_TO_FIX_TYPOS || typoCandidates.isEmpty() ?
          "json.schema.annotation.not.allowed.property" :
          "json.schema.annotation.not.allowed.property.possibly.typo", name),
                       nameValueAdapter != null ? nameValueAdapter.getDelegate() : property.getDelegate(),
                       JsonValidationError.FixableIssueKind.ProhibitedProperty,
                       new JsonValidationError.ProhibitedPropertyIssueData(
                         name,
                         name.length() >= MIN_LENGTH_TO_FIX_TYPOS ? typoCandidates : Collections.emptyList()
                       ), JsonErrorPriority.LOW_PRIORITY);
        isValid = false;
        if (options.shouldStopValidationAfterAnyErrorFound()) return false;
      }
      else if (ThreeState.UNSURE.equals(pair.getFirst()) && pair.second.getConstantSchema() == null) {
        for (JsonValueAdapter propertyValue : property.getValues()) {
          consumer.checkObjectBySchemaRecordErrors(pair.getSecond(), propertyValue);
          isValid &= consumer.getErrors().isEmpty();
          if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false;
        }
      }
      set.add(name);
    }
    reportMissingOptionalProperties(value, schema, consumer, options);

    if (object.shouldCheckIntegralRequirements() || options.isForceStrict()) {
      final Set<String> required = schema.getRequired();
      if (required != null) {
        HashSet<String> requiredNames = new LinkedHashSet<>(required);
        requiredNames.removeAll(set);
        if (!requiredNames.isEmpty()) {
          JsonValidationError.MissingMultiplePropsIssueData data = createMissingPropertiesData(schema, requiredNames, consumer, value);
          consumer.error(JsonBundle.message("schema.validation.missing.required.property.or.properties", data.getMessage(false)),
                         value.getDelegate(), JsonValidationError.FixableIssueKind.MissingProperty, data,
                         JsonErrorPriority.MISSING_PROPS);
          isValid = false;
          if (options.shouldStopValidationAfterAnyErrorFound()) return false;
        }
      }
      if (schema.getMinProperties() != null && propertyList.size() < schema.getMinProperties()) {
        consumer.error(JsonBundle.message("schema.validation.number.of.props.less.than", schema.getMinProperties()), value.getDelegate(),
                       JsonErrorPriority.LOW_PRIORITY);
        isValid = false;
        if (options.shouldStopValidationAfterAnyErrorFound()) return false;
      }
      if (schema.getMaxProperties() != null && propertyList.size() > schema.getMaxProperties()) {
        consumer.error(JsonBundle.message("schema.validation.number.of.props.greater.than", schema.getMaxProperties()), value.getDelegate(),
                       JsonErrorPriority.LOW_PRIORITY);
        isValid = false;
        if (options.shouldStopValidationAfterAnyErrorFound()) return false;
      }
      final Map<String, List<String>> dependencies = schema.getPropertyDependencies();
      if (dependencies != null) {
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
          if (set.contains(entry.getKey())) {
            final List<String> list = entry.getValue();
            HashSet<String> deps = new HashSet<>(list);
            deps.removeAll(set);
            if (!deps.isEmpty()) {
              JsonValidationError.MissingMultiplePropsIssueData data = createMissingPropertiesData(schema, deps, consumer, value);
              consumer.error(
                JsonBundle.message("schema.validation.violated.dependency", data.getMessage(false), entry.getKey()),
                value.getDelegate(),
                JsonValidationError.FixableIssueKind.MissingProperty,
                data, JsonErrorPriority.MISSING_PROPS);
              isValid = false;
              if (options.shouldStopValidationAfterAnyErrorFound()) return false;
            }
          }
        }
      }
      for (String name : StreamEx.of(schema.getSchemaDependencyNames())) {
        var dependency = schema.getSchemaDependencyByName(name);
        if (set.contains(name) && dependency != null) {
          consumer.checkObjectBySchemaRecordErrors(dependency, value);
          isValid &= consumer.getErrors().isEmpty();
          if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false;
        }
      }
    }
    return checkUnevaluatedPropertiesSchemaViolation(consumer, schema, object, options);
  }

  private static @NotNull ArrayList<@NlsSafe String> iteratorToList(Iterator<String> propertyNamesIterator) {
    return Collections.list(
      new Enumeration<>() {
        @Override
        public boolean hasMoreElements() {
          return propertyNamesIterator.hasNext();
        }

        @Override
        public String nextElement() {
          return propertyNamesIterator.next();
        }
      }
    );
  }

  private static boolean checkUnevaluatedPropertiesSchemaViolation(@NotNull JsonValidationHost consumer,
                                                                   @NotNull JsonSchemaObject schemaNode,
                                                                   @NotNull JsonObjectValueAdapter inspectedObject,
                                                                   @NotNull JsonComplianceCheckerOptions options) {
    var unevaluatedPropertiesSchema = schemaNode.getUnevaluatedPropertiesSchema();
    if (unevaluatedPropertiesSchema == null) return true;

    var constantSchemaValue = unevaluatedPropertiesSchema.getConstantSchema();
    if (Boolean.TRUE.equals(constantSchemaValue)) return true;

    var isValid = true;
    for (JsonPropertyAdapter childPropertyAdapter : inspectedObject.getPropertyList()) {
      if (isCoveredByAdjacentSchemas(consumer, childPropertyAdapter, schemaNode)) {
        continue;
      }

      JsonValueAdapter childPropertyNameAdapter = childPropertyAdapter.getNameValueAdapter();
      if (childPropertyNameAdapter == null) continue;

      consumer.checkObjectBySchemaRecordErrors(unevaluatedPropertiesSchema, childPropertyNameAdapter);
      isValid &= consumer.getErrors().isEmpty();
      if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false;
    }
    return isValid;
  }

  private static boolean isCoveredByAdjacentSchemas(@NotNull JsonValidationHost validationHost,
                                                    @NotNull JsonPropertyAdapter propertyAdapter,
                                                    @NotNull JsonSchemaObject schemaNode) {
    var instancePropertyName = propertyAdapter.getName();
    if (instancePropertyName != null && schemaNode.getPropertyByName(instancePropertyName) != null) return true;
    if (instancePropertyName != null && schemaNode.getMatchingPatternPropertySchema(instancePropertyName) != null) return true;
    if (!schemaNode.getAdditionalPropertiesAllowed()) return true;
    JsonSchemaObject additionalPropertiesSchema = schemaNode.getAdditionalPropertiesSchema();
    if (additionalPropertiesSchema != null && Boolean.TRUE.equals(additionalPropertiesSchema.getConstantSchema())) return true;
    JsonValueAdapter propertyNameAdapter = propertyAdapter.getNameValueAdapter();
    if (propertyNameAdapter != null && validationHost.hasRecordedErrorsFor(propertyNameAdapter)) return true;

    return false;
  }

  public static JsonValidationError.MissingMultiplePropsIssueData createMissingPropertiesData(@NotNull JsonSchemaObject schema,
                                                                                              Set<String> requiredNames,
                                                                                              JsonValidationHost consumer,
                                                                                              @NotNull JsonValueAdapter inspectedElementAdapter) {
    List<JsonValidationError.MissingPropertyIssueData> allProps = new ArrayList<>();
    for (String req : requiredNames) {
      JsonSchemaObject propertySchema = resolvePropertySchema(schema, req);
      Object defaultValue = propertySchema == null ? null : propertySchema.getDefault();
      if (defaultValue == null) {
        if (Registry.is("json.schema.object.v2")) {
          defaultValue = schema.getExampleByName(req);
        }
        else {
          var example = schema.getExample();
          defaultValue = example == null ? null : example.get(req);
        }
      }
      Ref<Integer> enumCount = Ref.create(0);

      JsonSchemaType type = null;

      if (propertySchema != null) {
        MatchResult result = null;
        Object valueFromEnum = getDefaultValueFromEnum(propertySchema, enumCount);
        if (valueFromEnum != null) {
          defaultValue = valueFromEnum;
        }
        else {
          result = consumer.resolve(propertySchema, inspectedElementAdapter);
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
            result = consumer.resolve(propertySchema, inspectedElementAdapter);
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
    var propOrNull = schema.getPropertyByName(req);
    if (propOrNull != null) {
      return propOrNull;
    }

    JsonSchemaObject propertySchema = schema.getMatchingPatternPropertySchema(req);
    if (propertySchema != null) {
      return propertySchema;
    }

    JsonSchemaObject additionalPropertiesSchema = schema.getAdditionalPropertiesSchema();
    if (additionalPropertiesSchema != null) {
      return additionalPropertiesSchema;
    }

    JsonSchemaObject unevaluatedPropertiesSchema = schema.getUnevaluatedPropertiesSchema();
    if (unevaluatedPropertiesSchema != null && unevaluatedPropertiesSchema.getConstantSchema() == null) {
      return unevaluatedPropertiesSchema;
    }

    return null;
  }


  private static @Nullable Object getDefaultValueFromEnum(@NotNull JsonSchemaObject propertySchema, @NotNull Ref<Integer> enumCount) {
    List<Object> enumValues = propertySchema.getEnum();
    if (enumValues != null) {
      enumCount.set(enumValues.size());
      if (!enumValues.isEmpty()) {
        Object defaultObject = enumValues.get(0);
        return defaultObject instanceof String ? StringUtil.unquoteString((String)defaultObject) : defaultObject;
      }
    }
    return null;
  }

  private static void reportMissingOptionalProperties(JsonValueAdapter inspectedValue,
                                                      JsonSchemaObject schema,
                                                      JsonValidationHost validationHost,
                                                      JsonComplianceCheckerOptions options) {
    var objectValueAdapter = inspectedValue.getAsObject();
    if (!options.isReportMissingOptionalProperties() || objectValueAdapter == null) {
      return;
    }

    var existingProperties = ContainerUtil.map(objectValueAdapter.getPropertyList(), JsonPropertyAdapter::getName);

    Iterable<String> iter = (() -> schema.getPropertyNames());
    var missingProperties =
      StreamSupport.stream(iter.spliterator(), false).filter(it -> !existingProperties.contains(it)).collect(Collectors.toSet());
    var missingPropertiesData = createMissingPropertiesData(schema, missingProperties, validationHost, objectValueAdapter);
    validationHost.error(
      JsonBundle.message("schema.validation.missing.not.required.property.or.properties", missingPropertiesData.getMessage(false)),
      inspectedValue.getDelegate(),
      JsonValidationError.FixableIssueKind.MissingOptionalProperty,
      missingPropertiesData,
      JsonErrorPriority.MISSING_PROPS);
  }
}
