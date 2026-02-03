// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

class ExcludeFromCompletionAction extends LookupElementAction {
  private final Project myProject;
  private final String myToExclude;

  ExcludeFromCompletionAction(@NotNull Project project, @NotNull String s) {
    super(null, JavaBundle.message("exclude.0.from.completion", s));
    myProject = project;
    myToExclude = s;
  }

  @Override
  public Result performLookupAction() {
    AddImportAction.excludeFromImport(myProject, myToExclude);
    return Result.HIDE_LOOKUP;
  }
}
