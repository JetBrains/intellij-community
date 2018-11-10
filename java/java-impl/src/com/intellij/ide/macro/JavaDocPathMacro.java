// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.javadoc.JavadocConfiguration;
import com.intellij.javadoc.JavadocGenerationManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

import java.io.File;

public final class JavaDocPathMacro extends Macro {
  @Override
  public String getName() {
    return "JavaDocPath";
  }

  @Override
  public String getDescription() {
    return IdeBundle.message("macro.javadoc.output.directory");
  }

  @Override
  public String expand(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    JavadocGenerationManager manager = project.getComponent(JavadocGenerationManager.class);
    if (manager == null) {
      return null;
    }
    final JavadocConfiguration configuration = manager.getConfiguration();
    return configuration.OUTPUT_DIRECTORY == null ? null : configuration.OUTPUT_DIRECTORY.replace('/', File.separatorChar);
  }
}
