// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.java.JavaBundle;
import com.intellij.javadoc.JavadocConfiguration;
import com.intellij.javadoc.JavadocGenerationManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;

final class JavaDocPathMacro extends Macro {
  @Override
  public @NotNull String getName() {
    return "JavaDocPath";
  }

  @Override
  public @NotNull String getDescription() {
    return JavaBundle.message("macro.javadoc.output.directory");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }

    JavadocGenerationManager manager = JavadocGenerationManager.getInstance(project);
    if (manager == null) {
      return null;
    }

    JavadocConfiguration configuration = manager.getConfiguration();
    return configuration.OUTPUT_DIRECTORY == null ? null : configuration.OUTPUT_DIRECTORY.replace('/', File.separatorChar);
  }
}
