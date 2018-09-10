/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;

import java.util.Map;

/**
 * @author nik
 */
public interface JpsPathVariablesConfiguration extends JpsElement {
  void addPathVariable(@NotNull String name, @NotNull String value);

  void removePathVariable(@NotNull String name);

  /**
   * @deprecated use {@link #getUserVariableValue(String)} instead
   */
  @Deprecated
  @Nullable
  String getPathVariable(@NotNull String name);

  @Nullable
  String getUserVariableValue(@NotNull String name);

  /**
   * @deprecated use {@link #getAllUserVariables()} instead
   */
  @Deprecated
  @NotNull
  Map<String, String> getAllVariables();

  @NotNull
  Map<String, String> getAllUserVariables();
}
