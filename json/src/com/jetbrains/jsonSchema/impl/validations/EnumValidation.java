// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.X_INTELLIJ_CASE_INSENSITIVE;

public final class EnumValidation implements JsonSchemaValidation {
  public static final EnumValidation INSTANCE = new EnumValidation();
  @Override
  public boolean validate(@NotNull JsonValueAdapter propValue,
                          @NotNull JsonSchemaObject schema,
                          @Nullable JsonSchemaType schemaType,
                          @NotNull JsonValidationHost consumer,
                          @NotNull JsonComplianceCheckerOptions options) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.EnumValidation);
    List<Object> enumItems = schema.getEnum();
    if (enumItems == null) return true;
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(propValue.getDelegate(), schema);
    if (walker == null) return true;
    final String text = StringUtil.notNullize(walker.getNodeTextForValidation(propValue.getDelegate()));
    boolean caseInsensitive = Boolean.parseBoolean(schema.readChildNodeValue(X_INTELLIJ_CASE_INSENSITIVE)) || schema.isForceCaseInsensitive();
    BiFunction<String, String, Boolean> eq = options.isCaseInsensitiveEnumCheck() || caseInsensitive
                                             ? String::equalsIgnoreCase
                                             : String::equals;
    for (Object object : enumItems) {
      if (checkEnumValue(object, walker, propValue, text, eq)) return true;
    }
    consumer.error(JsonBundle.message("schema.validation.enum.mismatch", StringUtil.join(enumItems, o -> o.toString(), ", ")), propValue.getDelegate(),
                   JsonValidationError.FixableIssueKind.NonEnumValue, null, JsonErrorPriority.MEDIUM_PRIORITY);
    return false;
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

  private static boolean equalsIgnoreQuotes(final @NotNull String s1,
                                            final @NotNull String s2,
                                            boolean requireQuotedValues,
                                            BiFunction<String, String, Boolean> eq) {
    final boolean quoted1 = StringUtil.isQuotedString(s1);
    final boolean quoted2 = StringUtil.isQuotedString(s2);
    if (requireQuotedValues && quoted1 != quoted2) return false;
    if (requireQuotedValues && !quoted1) return eq.apply(s1, s2);
    return eq.apply(StringUtil.unquoteString(s1), StringUtil.unquoteString(s2));
  }
}
