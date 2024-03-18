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
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class ArrayValidation implements JsonSchemaValidation {
  public static final ArrayValidation INSTANCE = new ArrayValidation();
  @Override
  public void validate(JsonValueAdapter propValue,
                       JsonSchemaObject schema,
                       JsonSchemaType schemaType,
                       JsonValidationHost consumer,
                       JsonComplianceCheckerOptions options) {
    checkArray(propValue, schema, consumer, options);
  }

  private static void checkArray(JsonValueAdapter value,
                                 JsonSchemaObject schema,
                                 JsonValidationHost consumer,
                                 JsonComplianceCheckerOptions options) {
    final JsonArrayValueAdapter asArray = value.getAsArray();
    if (asArray == null) return;
    final List<JsonValueAdapter> elements = asArray.getElements();
    checkArrayItems(value, elements, schema, consumer, options);
  }

  private static void checkArrayItems(@NotNull JsonValueAdapter array,
                                      final @NotNull List<JsonValueAdapter> list,
                                      final JsonSchemaObject schema,
                                      JsonValidationHost consumer,
                                      JsonComplianceCheckerOptions options) {
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
            consumer.error(JsonBundle.message("schema.validation.not.unique"), item.getDelegate(), JsonErrorPriority.TYPE_MISMATCH);
          }
        }
      }
    }
    if (schema.getContainsSchema() != null) {
      boolean match = false;
      for (JsonValueAdapter item: list) {
        final JsonValidationHost checker = consumer.checkByMatchResult(item, consumer.resolve(schema.getContainsSchema()), options);
        if (checker == null || checker.isValid()) {
          match = true;
          break;
        }
      }
      if (!match) {
        consumer.error(JsonBundle.message("schema.validation.array.not.contains"), array.getDelegate(), JsonErrorPriority.MEDIUM_PRIORITY);
      }
    }
    if (schema.getItemsSchema() != null) {
      for (JsonValueAdapter item : list) {
        consumer.checkObjectBySchemaRecordErrors(schema.getItemsSchema(), item);
      }
    }
    else if (schema.getItemsSchemaList() != null) {
      var iterator = schema.getItemsSchemaList().iterator();
      for (JsonValueAdapter arrayValue : list) {
        if (iterator.hasNext()) {
          consumer.checkObjectBySchemaRecordErrors(iterator.next(), arrayValue);
        }
        else {
          if (!Boolean.TRUE.equals(schema.getAdditionalItemsAllowed())) {
            consumer.error(JsonBundle.message("schema.validation.array.no.extra"), arrayValue.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
          }
          else if (schema.getAdditionalItemsSchema() != null) {
            consumer.checkObjectBySchemaRecordErrors(schema.getAdditionalItemsSchema(), arrayValue);
          }
        }
      }
    }
    if (schema.getMinItems() != null && list.size() < schema.getMinItems()) {
      consumer.error(JsonBundle.message("schema.validation.array.shorter.than", schema.getMinItems()), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
    }
    if (schema.getMaxItems() != null && list.size() > schema.getMaxItems()) {
      consumer.error(JsonBundle.message("schema.validation.array.longer.than",  schema.getMaxItems()), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
    }

    // these two are not correct by the schema spec, but are used in some schemas
    if (schema.getMinLength() != null && list.size() < schema.getMinLength()) {
      consumer.error(JsonBundle.message("schema.validation.array.shorter.than", schema.getMinLength()), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
    }
    if (schema.getMaxLength() != null && list.size() > schema.getMaxLength()) {
      consumer.error(JsonBundle.message("schema.validation.array.longer.than",  schema.getMaxLength()), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
    }
  }
}
