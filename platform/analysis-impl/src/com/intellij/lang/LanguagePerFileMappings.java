// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class LanguagePerFileMappings<T> extends PerFileMappingsBase<T> implements PerFileMappings<T> {

  public LanguagePerFileMappings(@NotNull Project project) {
    super(project);
  }

  @Override
  protected @NotNull Project getProject() {
    return Objects.requireNonNull(super.getProject());
  }

  @Override
  protected @NotNull String getValueAttribute() {
    return "dialect";
  }

}
