// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class LanguagePerFileMappings<T> extends PerFileMappingsBase<T> implements PerFileMappings<T> {

  public LanguagePerFileMappings(@NotNull Project project) {
    super(project);
  }

  @Override
  @NotNull
  protected Project getProject() {
    return Objects.requireNonNull(super.getProject());
  }

  @Override
  @NotNull
  protected String getValueAttribute() {
    return "dialect";
  }

}
