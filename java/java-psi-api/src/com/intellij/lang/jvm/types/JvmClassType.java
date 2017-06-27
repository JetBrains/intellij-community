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

import com.intellij.lang.jvm.JvmClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;

/**
 * Represents a type which could be resolved into a class and optionally has type arguments.
 */
public interface JvmClassType extends JvmReferenceType {

  /**
   * Resolves the class reference and returns the resulting class.
   *
   * @return the class instance, or null if the reference resolve failed.
   * @see ParameterizedType#getRawType
   */
  @Nullable
  @Override
  JvmClass resolve();

  /**
   * @return type arguments
   * @see ParameterizedType#getActualTypeArguments
   */
  @NotNull
  Iterable<JvmType> getTypeArguments();

  boolean hasTypeArguments();
}
