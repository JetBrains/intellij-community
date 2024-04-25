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
package com.jetbrains.jsonSchema.extension.adapters;

import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JsonValueAdapter {
  default boolean isShouldBeIgnored() {return false;}
  boolean isObject();
  boolean isArray();
  boolean isStringLiteral();
  boolean isNumberLiteral();
  boolean isBooleanLiteral();
  boolean isNull();

  /**
   * Returns whether this adapter represents just empty text.
   * <p>
   * In languages where empty objects or values are not denotable easily (e.g. YAML), it is sometimes necessary to know the difference.
   * For instance, an empty YAML document is considered an empty object, but is not exactly an object (inserting elements inside requires
   * some extra plumbing).
   */
  default boolean isEmptyAdapter() { return false; }

  @NotNull PsiElement getDelegate();

  @Nullable JsonObjectValueAdapter getAsObject();
  @Nullable JsonArrayValueAdapter getAsArray();

  default boolean shouldCheckIntegralRequirements() { return true; }
  default boolean shouldCheckAsValue() { return true; }

  /**
   * For some languages, the same node may represent values of different types depending on the context
   * This happens, for instance, in YAML, where empty objects and null values are the same thing
   */
  default JsonSchemaType getAlternateType(@Nullable JsonSchemaType type) { return type; }
}
