// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FormatOnSaveActionInfo extends ActionOnSaveInfo {
  private static final Key<Boolean> FORMAT_ON_SAVE_KEY = Key.create("format.on.save");
  private static final String FORMAT_ON_SAVE_PROPERTY = "format.on.save";
  private static final boolean FORMAT_ON_SAVE_DEFAULT = false;

  public static boolean isReformatOnSaveEnabled(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(FORMAT_ON_SAVE_PROPERTY, FORMAT_ON_SAVE_DEFAULT);
  }

  private static void setReformatOnSaveEnabled(@NotNull Project project, boolean enabled) {
    PropertiesComponent.getInstance(project).setValue(FORMAT_ON_SAVE_PROPERTY, enabled, FORMAT_ON_SAVE_DEFAULT);
  }

  public static class FormatOnSaveActionProvider extends ActionOnSaveInfoProvider {
    @Override
    protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
      // TODO correct the supported IDE list.
      if (PlatformUtils.isIntelliJ() || PlatformUtils.isWebStorm()) {
        return List.of(new FormatOnSaveActionInfo(context));
      }
      return Collections.emptyList();
    }
  }

  public FormatOnSaveActionInfo(@NotNull ActionOnSaveContext context) {
    super(context);
  }

  @Override
  public @NotNull String getActionOnSaveName() {
    return CodeInsightBundle.message("actions.on.save.page.checkbox.reformat.code");
  }

  @Override
  public boolean isActionOnSaveEnabled() {
    Boolean data = getContext().getUserData(FORMAT_ON_SAVE_KEY);
    return data != null ? data : isReformatOnSaveEnabled(getProject());
  }

  @Override
  public void setActionOnSaveEnabled(boolean enabled) {
    getContext().putUserData(FORMAT_ON_SAVE_KEY, enabled);
  }

  @Override
  protected void apply() {
    setReformatOnSaveEnabled(getProject(), isActionOnSaveEnabled());
  }

  @Override
  protected boolean isModified() {
    return isReformatOnSaveEnabled(getProject()) != isActionOnSaveEnabled();
  }
}
