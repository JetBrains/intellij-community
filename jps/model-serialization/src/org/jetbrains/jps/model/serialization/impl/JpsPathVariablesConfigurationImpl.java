// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.serialization.JpsPathVariablesConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JpsPathVariablesConfigurationImpl extends JpsElementBase<JpsPathVariablesConfigurationImpl> implements JpsPathVariablesConfiguration {
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
  public void addPathVariable(@NotNull String name, @NotNull String value) {
    myPathVariables.put(name, value);
  }

  @Override
  public void removePathVariable(@NotNull String name) {
    myPathVariables.remove(name);
  }

  @Nullable
  @Override
  public String getUserVariableValue(@NotNull String name) {
    return myPathVariables.get(name);
  }

  @NotNull
  @Override
  public Map<String, String> getAllUserVariables() {
    return Collections.unmodifiableMap(myPathVariables);
  }
}
