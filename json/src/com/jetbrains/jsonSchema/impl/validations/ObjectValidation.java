// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl.validations;

import com.intellij.json.JsonBundle;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder.doSingleStep;

public class ObjectValidation implements JsonSchemaValidation {
  public static final ObjectValidation INSTANCE = new ObjectValidation();
  @Override
  public void validate(JsonValueAdapter propValue,
                       JsonSchemaObject schema,
                       JsonSchemaType schemaType,
                       JsonValidationHost consumer,
                       JsonComplianceCheckerOptions options) {
    checkObject(propValue, schema, consumer, options);
  }
  private static void checkObject(@NotNull JsonValueAdapter value,
                                  @NotNull JsonSchemaObject schema,
                                  JsonValidationHost consumer, JsonComplianceCheckerOptions options) {
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
          JsonValidationHost checker = consumer.checkByMatchResult(nameValueAdapter, consumer.resolve(propertyNamesSchema), options);
          if (checker != null) {
            consumer.addErrorsFrom(checker);
          }
        }
      }

      final JsonPointerPosition step = JsonPointerPosition.createSingleProperty(name);
      final Pair<ThreeState, JsonSchemaObject> pair = doSingleStep(step, schema, false);
      if (ThreeState.NO.equals(pair.getFirst()) && !set.contains(name)) {
        consumer.error(JsonBundle.message("json.schema.annotation.not.allowed.property", name), property.getDelegate(),
              JsonValidationError.FixableIssueKind.ProhibitedProperty,
              new JsonValidationError.ProhibitedPropertyIssueData(name), JsonErrorPriority.LOW_PRIORITY);
      }
      else if (ThreeState.UNSURE.equals(pair.getFirst())) {
        for (JsonValueAdapter propertyValue : property.getValues()) {
          consumer.checkObjectBySchemaRecordErrors(pair.getSecond(), propertyValue);
        }
      }
      set.add(name);
    }

    if (object.shouldCheckIntegralRequirements() || options.isForceStrict()) {
      final Set<String> required = schema.getRequired();
      if (required != null) {
        HashSet<String> requiredNames = new LinkedHashSet<>(required);
        requiredNames.removeAll(set);
        if (!requiredNames.isEmpty()) {
          JsonValidationError.MissingMultiplePropsIssueData data = createMissingPropertiesData(schema, requiredNames, consumer);
          consumer.error(JsonBundle.message("schema.validation.missing.required.property.or.properties",  data.getMessage(false)), value.getDelegate(), JsonValidationError.FixableIssueKind.MissingProperty, data,
                JsonErrorPriority.MISSING_PROPS);
        }
      }
      if (schema.getMinProperties() != null && propertyList.size() < schema.getMinProperties()) {
        consumer.error(JsonBundle.message("schema.validation.number.of.props.less.than", schema.getMinProperties()), value.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
      }
      if (schema.getMaxProperties() != null && propertyList.size() > schema.getMaxProperties()) {
        consumer.error(JsonBundle.message("schema.validation.number.of.props.greater.than", schema.getMaxProperties()), value.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
      }
      final Map<String, List<String>> dependencies = schema.getPropertyDependencies();
      if (dependencies != null) {
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
          if (set.contains(entry.getKey())) {
            final List<String> list = entry.getValue();
            HashSet<String> deps = new HashSet<>(list);
            deps.removeAll(set);
            if (!deps.isEmpty()) {
              JsonValidationError.MissingMultiplePropsIssueData data = createMissingPropertiesData(schema, deps, consumer);
              consumer.error(
                JsonBundle.message("schema.validation.violated.dependency", data.getMessage(false), entry.getKey()),
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
            consumer.checkObjectBySchemaRecordErrors(entry.getValue(), value);
          }
        }
      }
    }
  }

  private static JsonValidationError.MissingMultiplePropsIssueData createMissingPropertiesData(@NotNull JsonSchemaObject schema,
                                                                                               HashSet<String> requiredNames,
                                                                                               JsonValidationHost consumer) {
    List<JsonValidationError.MissingPropertyIssueData> allProps = new ArrayList<>();
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
          result = consumer.resolve(propertySchema);
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
            result = consumer.resolve(propertySchema);
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
}
