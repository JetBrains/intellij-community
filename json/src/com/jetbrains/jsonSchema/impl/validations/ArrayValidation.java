// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl.validations;

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ArrayValidation implements JsonSchemaValidation {
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
    if (schema.getMinLength() != null && elements.size() < schema.getMinLength()) {
      consumer.error("Array is shorter than " + schema.getMinLength(), value.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
      return;
    }
    checkArrayItems(value, elements, schema, consumer, options);
  }

  private static void checkArrayItems(@NotNull JsonValueAdapter array,
                                      @NotNull final List<JsonValueAdapter> list,
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
            consumer.error("Item is not unique", item.getDelegate(), JsonErrorPriority.TYPE_MISMATCH);
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
        consumer.error("No match for 'contains' rule", array.getDelegate(), JsonErrorPriority.MEDIUM_PRIORITY);
      }
    }
    if (schema.getItemsSchema() != null) {
      for (JsonValueAdapter item : list) {
        consumer.checkObjectBySchemaRecordErrors(schema.getItemsSchema(), item);
      }
    }
    else if (schema.getItemsSchemaList() != null) {
      final Iterator<JsonSchemaObject> iterator = schema.getItemsSchemaList().iterator();
      for (JsonValueAdapter arrayValue : list) {
        if (iterator.hasNext()) {
          consumer.checkObjectBySchemaRecordErrors(iterator.next(), arrayValue);
        }
        else {
          if (!Boolean.TRUE.equals(schema.getAdditionalItemsAllowed())) {
            consumer.error("Additional items are not allowed", arrayValue.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
          }
          else if (schema.getAdditionalItemsSchema() != null) {
            consumer.checkObjectBySchemaRecordErrors(schema.getAdditionalItemsSchema(), arrayValue);
          }
        }
      }
    }
    if (schema.getMinItems() != null && list.size() < schema.getMinItems()) {
      consumer.error("Array is shorter than " + schema.getMinItems(), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
    }
    if (schema.getMaxItems() != null && list.size() > schema.getMaxItems()) {
      consumer.error("Array is longer than " + schema.getMaxItems(), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
    }
  }
}
