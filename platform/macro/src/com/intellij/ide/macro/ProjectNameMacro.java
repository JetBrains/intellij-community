// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class ProjectNameMacro extends Macro {
  @Override
  public @NotNull String getName() {
    return "ProjectName";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.project.file.name");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    return project.getName();
  }
}
