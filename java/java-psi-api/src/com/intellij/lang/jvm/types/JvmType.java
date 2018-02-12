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

import com.intellij.lang.jvm.JvmAnnotation;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an type which is supported by JVM.
 *
 * @see java.lang.reflect.Type
 * @see java.lang.reflect.AnnotatedType
 */
public interface JvmType {

  /**
   * @return type annotations
   * @see java.lang.reflect.AnnotatedType#getAnnotations
   */
  @NotNull
  JvmAnnotation[] getAnnotations();
}
