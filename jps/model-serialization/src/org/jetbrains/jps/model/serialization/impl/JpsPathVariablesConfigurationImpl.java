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
package org.jetbrains.jps.model.serialization.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.serialization.JpsPathVariablesConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JpsPathVariablesConfigurationImpl extends JpsElementBase<JpsPathVariablesConfigurationImpl> implements JpsPathVariablesConfiguration {
  private final Map<String, String> myPathVariables;

  public JpsPathVariablesConfigurationImpl() {
    myPathVariables = new LinkedHashMap<>();
  }

  private JpsPathVariablesConfigurationImpl(Map<String, String> pathVariables) {
    myPathVariables = new LinkedHashMap<>(pathVariables);
  }

  @NotNull
  @Override
  public JpsPathVariablesConfigurationImpl createCopy() {
    return new JpsPathVariablesConfigurationImpl(myPathVariables);
  }

  @Override
  public void applyChanges(@NotNull JpsPathVariablesConfigurationImpl modified) {
  }

  @Override
  public void addPathVariable(@NotNull String name, @NotNull String value) {
    myPathVariables.put(name, value);
  }

  @Override
  public void removePathVariable(@NotNull String name) {
    myPathVariables.remove(name);
  }

  @Nullable
  @Override
  public String getPathVariable(@NotNull String name) {
    return getUserVariableValue(name);
  }

  @Nullable
  @Override
  public String getUserVariableValue(@NotNull String name) {
    return myPathVariables.get(name);
  }

  @NotNull
  @Override
  public Map<String, String> getAllVariables() {
    return getAllUserVariables();
  }

  @NotNull
  @Override
  public Map<String, String> getAllUserVariables() {
    return Collections.unmodifiableMap(myPathVariables);
  }
}
