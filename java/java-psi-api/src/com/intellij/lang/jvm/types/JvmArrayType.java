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

/**
 * @see Class#isArray
 */
public interface JvmArrayType extends JvmType {

  /**
   * @return component type of an array. That is:
   * <ul>
   * <li> for {@code int[]}: {@code int}
   * <li> for {@code String[][]}: {@code String[]}
   * </ul>
   * @see Class#getComponentType
   */
  @NotNull
  JvmType getComponentType();
}
