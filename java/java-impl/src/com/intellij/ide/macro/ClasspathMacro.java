// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import org.jetbrains.annotations.NotNull;

public final class ClasspathMacro extends Macro implements PathListMacro {
  @NotNull
  @Override
  public String getName() {
    return "Classpath";
  }

  @NotNull
  @Override
  public String getDescription() {
    return JavaBundle.message("macro.project.classpath");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    return OrderEnumerator.orderEntries(project).getPathsList().getPathsString();
  }
}
