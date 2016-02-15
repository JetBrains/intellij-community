package com.jetbrains.jsonSchema.extension;


import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public interface JsonSchemaProviderFactory {
  ExtensionPointName<JsonSchemaProviderFactory> EP_NAME = ExtensionPointName.create("JavaScript.JsonSchema.ProviderFactory");

  JsonSchemaFileProvider[] getProviders(@Nullable Project project);
}
