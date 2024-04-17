// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.Nullable;

public interface JsonSchemaCompletionHandlerProvider {
  ExtensionPointName<JsonSchemaCompletionHandlerProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.json.jsonSchemaCompletionHandlerProvider");

  default @Nullable InsertHandler<LookupElement> createHandlerForEnumValue(JsonSchemaObject schema, String value) {
    return null;
  }
}
