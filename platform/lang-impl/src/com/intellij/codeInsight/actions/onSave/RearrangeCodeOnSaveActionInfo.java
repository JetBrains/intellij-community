// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class RearrangeCodeOnSaveActionInfo extends ActionOnSaveInfoBase {
  private static final String REARRANGE_CODE_ON_SAVE_PROPERTY = "rearrange.code.on.save";
  private static final boolean REARRANGE_CODE_ON_SAVE_DEFAULT = false;

  public static boolean isRearrangeCodeOnSaveEnabled(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(REARRANGE_CODE_ON_SAVE_PROPERTY, REARRANGE_CODE_ON_SAVE_DEFAULT);
  }

  public RearrangeCodeOnSaveActionInfo(@NotNull ActionOnSaveContext context) {
    super(context,
          CodeInsightBundle.message("actions.on.save.page.checkbox.rearrange.code"),
          REARRANGE_CODE_ON_SAVE_PROPERTY,
          REARRANGE_CODE_ON_SAVE_DEFAULT);
  }
}
