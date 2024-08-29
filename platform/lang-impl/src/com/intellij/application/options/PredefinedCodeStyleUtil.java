// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.PredefinedCodeStyle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PredefinedCodeStyleUtil {
  private PredefinedCodeStyleUtil() {}

  public static boolean isCodeStyleSet(@NotNull Project project, @NotNull PredefinedCodeStyle predefinedCodeStyle) {
    CodeStyleSettings styleSettings = CodeStyle.getSettings(project);
    Ref<Boolean> isEqual = Ref.create(false);
    CodeStyle.runWithLocalSettings(project, styleSettings, (cloneSettings) -> {
      predefinedCodeStyle.apply(cloneSettings);
      isEqual.set(styleSettings.equals(cloneSettings));
    });

    return isEqual.get();
  }

  public static void setCodeStylesToProject(@NotNull Project project, PredefinedCodeStyle @NotNull ... styles) {
    CodeStyleSchemesModel codeStyleSchemesModel = new CodeStyleSchemesModel(project);
    CodeStyleScheme scheme = codeStyleSchemesModel.getSelectedScheme();

    if (!codeStyleSchemesModel.isProjectScheme(scheme)) {
      codeStyleSchemesModel.copyToProject(scheme);
      scheme = codeStyleSchemesModel.getProjectScheme();
    }

    CodeStyleSettings newSettings = scheme.getCodeStyleSettings();
    for (PredefinedCodeStyle extension : styles) {
      extension.apply(newSettings);
    }
    codeStyleSchemesModel.apply();
  }
}
