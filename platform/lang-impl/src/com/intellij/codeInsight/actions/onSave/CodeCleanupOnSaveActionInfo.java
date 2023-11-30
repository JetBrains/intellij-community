// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.actionsOnSave.ActionOnSaveComment;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ui.header.InspectionToolsConfigurable;
import com.intellij.ui.components.ActionLink;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CodeCleanupOnSaveActionInfo extends ActionOnSaveInfoBase {
  private static final String CODE_CLEANUP_ON_SAVE_PROPERTY = "code.cleanup.on.save";
  private static final boolean CODE_CLEANUP_ON_SAVE_DEFAULT = false;

  public static boolean isCodeCleanupOnSaveEnabled(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(CODE_CLEANUP_ON_SAVE_PROPERTY, CODE_CLEANUP_ON_SAVE_DEFAULT);
  }

  public CodeCleanupOnSaveActionInfo(@NotNull ActionOnSaveContext context) {
    super(context,
          CodeInsightBundle.message("actions.on.save.page.checkbox.run.code.cleanup"),
          CODE_CLEANUP_ON_SAVE_PROPERTY,
          CODE_CLEANUP_ON_SAVE_DEFAULT);
  }

  @Override
  public ActionOnSaveComment getComment() {
    return ActionOnSaveComment.info(CodeInsightBundle.message("actions.on.save.page.code.cleanup.comment"));
  }

  @Override
  public @NotNull List<? extends ActionLink> getActionLinks() {
    return List.of(createGoToPageInSettingsLink(CodeInsightBundle.message("actions.on.save.page.link.configure.inspections"),
                                                InspectionToolsConfigurable.ID));
  }
}
