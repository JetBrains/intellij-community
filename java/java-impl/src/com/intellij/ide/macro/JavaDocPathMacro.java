// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  @Override
  public String getName() {
    return "JavaDocPath";
  }

  @NotNull
  @Override
  public String getDescription() {
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
