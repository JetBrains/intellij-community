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
package com.intellij.lang.jvm.types;

import com.intellij.lang.jvm.JvmTypeDeclaration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a type which could be resolved into a class or a type parameter and optionally has type arguments.
 * <br/>
 * Such type appears in throws, bounds, extends, implements.
 * <p>
 * While {@link Class} and {@link java.lang.reflect.TypeVariable TypeVariable}
 * <b>are</b> {@link java.lang.reflect.Type types} because they are resolved at runtime,
 * this interface defines the contract for delaying actual resolution of the type declaration.
 *
 * @see java.lang.reflect.ParameterizedType
 */
public interface JvmReferenceType extends JvmType {

  JvmReferenceType[] EMPTY_ARRAY = new JvmReferenceType[0];

  @NotNull
  String getName();

  /**
   * @return declaration that declares this type or {@code null} if it cannot be resolved
   */
  @Nullable
  default JvmTypeDeclaration resolve() {
    JvmTypeResolveResult result = resolveType();
    return result == null ? null : result.getDeclaration();
  }

  /**
   * @return resolve result or {@code null} if it cannot be resolved
   */
  @Nullable
  JvmTypeResolveResult resolveType();

  /**
   * @return type arguments or empty iterable if this type is not a parameterized type
   * @see java.lang.reflect.ParameterizedType#getActualTypeArguments
   */
  @NotNull
  Iterable<JvmType> typeArguments();
}
