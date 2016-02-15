package com.jetbrains.jsonSchema.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;


public class JsonSchemaPropertyProcessor {
  public interface PropertyProcessor {
    /**
     * @return {@code true} to continue processing or {@code false} to stop.
     */
    boolean process(String name, JsonSchemaObject schema);
  }

  public static boolean process(@NotNull PropertyProcessor processor,
                                @NotNull JsonSchemaObject startSchema) {
    if (!processForVariants(processor, startSchema.getAnyOf()) ||
        !processForVariants(processor, startSchema.getOneOf()) ||
        !processForVariants(processor, startSchema.getAllOf())) {
      return false;
    }

    final Map<String, JsonSchemaObject> properties = startSchema.getProperties();
    for (Map.Entry<String, JsonSchemaObject> entry : properties.entrySet()) {
      if (!processor.process(entry.getKey(), entry.getValue())) {
        return false;
      }
    }

    return true;
  }

  private static boolean processForVariants(@NotNull PropertyProcessor processor,
                                            @Nullable List<JsonSchemaObject> list) {
    if (list != null && list.size() > 0) {
      for (JsonSchemaObject schemaObject : list) {
        if (!process(processor, schemaObject)) {
          return false;
        }
      }
    }

    return true;
  }
}
