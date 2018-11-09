/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;

/**
 * @author Irina.Chernushina on 3/7/2017.
 */
public interface JsonLikePsiWalkerFactory {
  ExtensionPointName<JsonLikePsiWalkerFactory> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.json.jsonLikePsiWalkerFactory");

  boolean handles(@NotNull PsiElement element);

  @NotNull
  JsonLikePsiWalker create(@NotNull JsonSchemaObject schemaObject);
}
