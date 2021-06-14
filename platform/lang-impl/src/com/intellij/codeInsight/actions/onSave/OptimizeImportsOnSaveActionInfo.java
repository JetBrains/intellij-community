// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class OptimizeImportsOnSaveActionInfo extends ActionOnSaveInfoBase {
  private static final String OPTIMIZE_IMPORTS_ON_SAVE_PROPERTY = "optimize.imports.on.save";
  private static final boolean OPTIMIZE_IMPORTS_ON_SAVE_DEFAULT = false;

  public static boolean isOptimizeImportsOnSaveEnabled(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(OPTIMIZE_IMPORTS_ON_SAVE_PROPERTY, OPTIMIZE_IMPORTS_ON_SAVE_DEFAULT);
  }

  public OptimizeImportsOnSaveActionInfo(@NotNull ActionOnSaveContext context) {
    super(context,
          CodeInsightBundle.message("actions.on.save.page.checkbox.optimize.imports"),
          OPTIMIZE_IMPORTS_ON_SAVE_PROPERTY,
          OPTIMIZE_IMPORTS_ON_SAVE_DEFAULT);
  }
}
