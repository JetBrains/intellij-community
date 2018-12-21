// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.execution;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TaskSettingsImpl implements TaskSettings {

  @NotNull private final String name;
  @NotNull private final List<String> arguments;
  @Nullable private final String description;

  public TaskSettingsImpl(@NotNull String name) {
    this(name, new ArrayList<>(), null);
  }

  public TaskSettingsImpl(@NotNull String name, @NotNull List<String> arguments) {
    this(name, arguments, null);
  }

  public TaskSettingsImpl(@NotNull String name, @Nullable String description) {
    this(name, new ArrayList<>(), description);
  }

  public TaskSettingsImpl(@NotNull String name, @NotNull List<String> arguments, @Nullable String description) {
    this.name = name;
    this.arguments = arguments;
    this.description = description;
  }

  @Nullable
  @Override
  public String getDescription() {
    return description;
  }

  @NotNull
  @Override
  public String getName() {
    return name;
  }

  @Unmodifiable
  @NotNull
  @Override
  public List<String> getArguments() {
    return ContainerUtil.immutableList(arguments);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TaskSettings settings = (TaskSettings)o;
    return name.equals(settings.getName()) &&
           arguments.equals(settings.getArguments());
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, arguments);
  }
}
