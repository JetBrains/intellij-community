package com.jetbrains.jsonSchema.extension;


import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface JsonSchemaProviderFactory<T> {
  ExtensionPointName<JsonSchemaProviderFactory> EP_NAME = ExtensionPointName.create("JavaScript.JsonSchema.ProviderFactory");

  List<JsonSchemaFileProvider<T>> getProviders(@Nullable Project project);
}
