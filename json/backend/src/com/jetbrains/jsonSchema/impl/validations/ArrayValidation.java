// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import com.intellij.json.JsonBundle;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.JsonValidationError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ArrayValidation implements JsonSchemaValidation {
  public static final ArrayValidation INSTANCE = new ArrayValidation();
  @Override
  public boolean validate(@NotNull JsonValueAdapter propValue,
                          @NotNull JsonSchemaObject schema,
                          @Nullable JsonSchemaType schemaType,
                          @NotNull JsonValidationHost consumer,
                          @NotNull JsonComplianceCheckerOptions options) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.ArrayValidation);
    return checkArray(propValue, schema, consumer, options);
  }

  private boolean checkArray(JsonValueAdapter value,
                                 JsonSchemaObject schema,
                                 JsonValidationHost consumer,
                                 JsonComplianceCheckerOptions options) {
    final JsonArrayValueAdapter asArray = value.getAsArray();
    if (asArray == null) return true;
    final List<JsonValueAdapter> elements = asArray.getElements();
    return checkArrayItems(value, elements, schema, consumer, options);
  }

  protected boolean checkArrayItems(@NotNull JsonValueAdapter array,
                                         final @NotNull List<JsonValueAdapter> list,
                                         final JsonSchemaObject schema,
                                         JsonValidationHost consumer,
                                         JsonComplianceCheckerOptions options) {
    if (options.shouldStopValidationAfterAnyErrorFound()) {
      return validateUniqueItems(array, list, schema, consumer, options) &&
             validateAgainstContainsSchema(array, list, schema, consumer, options) &&
             validateIndividualItems(list, schema, consumer, options) &&
             validateArrayLength(array, list, schema, consumer, options) &&
             validateArrayLengthHeuristically(array, list, schema, consumer, options);
    }
    else {
      return validateUniqueItems(array, list, schema, consumer, options) &
             validateAgainstContainsSchema(array, list, schema, consumer, options) &
             validateIndividualItems(list, schema, consumer, options) &
             validateArrayLength(array, list, schema, consumer, options) &
             validateArrayLengthHeuristically(array, list, schema, consumer, options);
    }
  }

  protected boolean validateIndividualItems(@NotNull List<JsonValueAdapter> list,
                                                 JsonSchemaObject schema,
                                                 JsonValidationHost consumer,
                                                 JsonComplianceCheckerOptions options) {
    var isValid = true;

    if (schema.getItemsSchema() != null) {
      for (JsonValueAdapter item : list) {
        consumer.checkObjectBySchemaRecordErrors(schema.getItemsSchema(), item);
        isValid &= consumer.getErrors().isEmpty();
        if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false;
      }
    }
    else if (schema.getItemsSchemaList() != null) {
      var iterator = schema.getItemsSchemaList().iterator();
      for (JsonValueAdapter arrayValue : list) {
        if (iterator.hasNext()) {
          consumer.checkObjectBySchemaRecordErrors(iterator.next(), arrayValue);
          isValid &= consumer.getErrors().isEmpty();
          if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false;
        }
        else {
          if (!Boolean.TRUE.equals(schema.getAdditionalItemsAllowed())) {
            consumer.error(JsonBundle.message("schema.validation.array.no.extra"), arrayValue.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
            isValid = false;
            if (options.shouldStopValidationAfterAnyErrorFound()) return false;
          }
          else if (schema.getAdditionalItemsSchema() != null) {
            consumer.checkObjectBySchemaRecordErrors(schema.getAdditionalItemsSchema(), arrayValue);
            isValid &= consumer.getErrors().isEmpty();
            if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false;
          }
        }
      }
    }

    return isValid;
  }

  protected static boolean validateArrayLengthHeuristically(@NotNull JsonValueAdapter array,
                                                         @NotNull List<JsonValueAdapter> list,
                                                         JsonSchemaObject schema,
                                                         JsonValidationHost consumer,
                                                         JsonComplianceCheckerOptions options) {
    // these two are not correct by the schema spec, but are used in some schemas
    if (schema.getMinLength() != null && list.size() < schema.getMinLength()) {
      consumer.error(JsonBundle.message("schema.validation.array.shorter.than", schema.getMinLength()), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
      return false;
    }
    if (schema.getMaxLength() != null && list.size() > schema.getMaxLength()) {
      consumer.error(JsonBundle.message("schema.validation.array.longer.than", schema.getMaxLength()), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
      return false;
    }
    return true;
  }

  protected static boolean validateArrayLength(@NotNull JsonValueAdapter array,
                                            @NotNull List<JsonValueAdapter> list,
                                            JsonSchemaObject schema,
                                            JsonValidationHost consumer, JsonComplianceCheckerOptions options) {
    if (schema.getMinItems() != null && list.size() < schema.getMinItems()) {
      consumer.error(JsonBundle.message("schema.validation.array.shorter.than", schema.getMinItems()), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
      return false;
    }
    if (schema.getMaxItems() != null && list.size() > schema.getMaxItems()) {
      consumer.error(JsonBundle.message("schema.validation.array.longer.than", schema.getMaxItems()), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
      return false;
    }
    return true;
  }

  protected static boolean validateAgainstContainsSchema(@NotNull JsonValueAdapter array,
                                @NotNull List<JsonValueAdapter> list,
                                JsonSchemaObject schema,
                                JsonValidationHost consumer,
                                JsonComplianceCheckerOptions options) {
    if (schema.getContainsSchema() != null) {
      boolean match = false;
      for (JsonValueAdapter item: list) {
        final JsonValidationHost checker = consumer.checkByMatchResult(item, consumer.resolve(schema.getContainsSchema(), array), options);
        if (checker == null || checker.isValid()) {
          match = true;
          break;
        }
      }
      if (!match) {
        consumer.error(JsonBundle.message("schema.validation.array.not.contains"), array.getDelegate(), JsonErrorPriority.MEDIUM_PRIORITY);
        return false;
      }
    }
    return true;
  }

  protected static boolean validateUniqueItems(@NotNull JsonValueAdapter array,
                                @NotNull List<JsonValueAdapter> list,
                                JsonSchemaObject schema,
                                JsonValidationHost consumer,
                                @NotNull JsonComplianceCheckerOptions options) {
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
            consumer.error(JsonBundle.message("schema.validation.not.unique"), item.getDelegate(),
                           JsonValidationError.FixableIssueKind.DuplicateArrayItem,
                           new JsonValidationError.DuplicateArrayItemIssueData(
                             entry.getValue().stream().mapToInt(v -> list.indexOf(v)).toArray()
                           ),
                           JsonErrorPriority.TYPE_MISMATCH);
            if (options.shouldStopValidationAfterAnyErrorFound()) return false;
          }
        }
      }
    }
    return true;
  }
}
