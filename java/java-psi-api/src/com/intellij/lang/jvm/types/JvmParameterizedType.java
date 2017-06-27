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

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;

/**
 * Represents a type with type arguments.
 *
 * @see ParameterizedType
 */
public interface JvmParameterizedType extends JvmType {

  /**
   * Returns {@link JvmClassType} that declared this type, for example {@code List} in {@code List<Integer>}.
   *
   * @return the {@link JvmClassType} that declared this type
   * @see ParameterizedType#getRawType
   */
  @NotNull
  JvmClassType getClassType();

  /**
   * Possible type arguments are {@link JvmBoundType} and {@link JvmWildcardType}.
   *
   * @return type arguments
   * @see ParameterizedType#getActualTypeArguments
   */
  @NotNull
  Iterable<JvmType> getTypeArguments();

  boolean hasTypeArguments();
}
