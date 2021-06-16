// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.options;

import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.ide.actionsOnSave.*;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.ui.components.ActionLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BuildOnSaveInfo extends ActionOnSaveBackedByOwnConfigurable<CompilerConfigurable> {

  public static class BuildOnSaveInfoProvider extends ActionOnSaveInfoProvider {
    @Override
    protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
      for (CompilerOptionsFilter filter : CompilerOptionsFilter.EP_NAME.getExtensionList()) {
        if (!filter.isAvailable(CompilerOptionsFilter.Setting.AUTO_MAKE, context.getProject()) ||
            !filter.isAvailable(CompilerOptionsFilter.Setting.EXTERNAL_BUILD, context.getProject()) &&
            CompilerUIConfigurable.EXTERNAL_BUILD_SETTINGS.contains(CompilerOptionsFilter.Setting.AUTO_MAKE)) {
          // The option "Build project automatically" is not available in Android Studio on the CompilerConfigurable page.
          // It is however available on the GradleCompilerSettingsConfigurable page.
          return Collections.emptyList();
        }
      }

      return List.of(new BuildOnSaveInfo(context));
    }
  }


  private BuildOnSaveInfo(@NotNull ActionOnSaveContext context) {
    super(context, CompilerConfigurable.CONFIGURABLE_ID, CompilerConfigurable.class);
  }

  @Override
  public @NotNull String getActionOnSaveName() {
    return JavaCompilerBundle.message("settings.actions.on.save.page.build.project.on.save.checkbox");
  }

  @Override
  protected @Nullable ActionOnSaveComment getCommentAccordingToStoredState() {
    return ActionOnSaveComment.info(JavaCompilerBundle.message("settings.actions.on.save.page.build.project.on.save.checkbox.comment"));
  }

  @Override
  protected @Nullable ActionOnSaveComment getCommentAccordingToUiState(@NotNull CompilerConfigurable configurable) {
    return ActionOnSaveComment.info(JavaCompilerBundle.message("settings.actions.on.save.page.build.project.on.save.checkbox.comment"));
  }

  @Override
  protected boolean isActionOnSaveEnabledAccordingToStoredState() {
    return CompilerWorkspaceConfiguration.getInstance(getProject()).MAKE_PROJECT_ON_SAVE;
  }

  @Override
  protected boolean isActionOnSaveEnabledAccordingToUiState(@NotNull CompilerConfigurable configurable) {
    return configurable.getCompilerUIConfigurable().getBuildOnSaveCheckBox().isSelected();
  }

  @Override
  protected void setActionOnSaveEnabled(@NotNull CompilerConfigurable configurable, boolean enabled) {
    configurable.getCompilerUIConfigurable().getBuildOnSaveCheckBox().setSelected(enabled);
  }

  @Override
  public @NotNull List<? extends ActionLink> getActionLinks() {
    String linkText = JavaCompilerBundle.message("settings.actions.on.save.page.compiler.settings.link");
    return List.of(createGoToPageInSettingsLink(linkText, CompilerConfigurable.CONFIGURABLE_ID));
  }

  @Override
  protected @NotNull String getActivatedOnDefaultText() {
    return getAnySaveAndExternalChangeText();
  }
}
