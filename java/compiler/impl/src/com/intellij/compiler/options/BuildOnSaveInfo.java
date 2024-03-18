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
  /**
   * This provider returns {@link BuildOnSaveInfo} only if the 'Build project automatically' checkbox is available on the 'Compiler' page in
   * Settings (Preferences). This may be not the case for Android projects (thanks to {@link com.android.tools.idea.gradle.project.build.compiler.HideCompilerOptions}).
   * In Android Studio, the 'Build project automatically' checkbox is available on the {@link com.android.tools.idea.gradle.project.build.compiler.GradleCompilerSettingsConfigurable}
   * page, and the 'Build project' check box will be contributed to the 'Action on Save' page by {@link com.android.tools.idea.gradle.project.build.compiler.GradleCompilerSettingsConfigurable.BuildOnSaveInfoProvider}.
   */
  public static final class BuildOnSaveInfoProvider extends ActionOnSaveInfoProvider {
    @Override
    protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
      if (context.getSettings().find(CompilerConfigurable.CONFIGURABLE_ID) == null) {
        // The standard 'Compiler' page may be absent in Android Studio
        return Collections.emptyList();
      }

      for (CompilerOptionsFilter filter : CompilerOptionsFilter.EP_NAME.getExtensionList()) {
        if (!filter.isAvailable(CompilerOptionsFilter.Setting.AUTO_MAKE, context.getProject()) ||
            !filter.isAvailable(CompilerOptionsFilter.Setting.EXTERNAL_BUILD, context.getProject()) &&
            CompilerUIConfigurable.EXTERNAL_BUILD_SETTINGS.contains(CompilerOptionsFilter.Setting.AUTO_MAKE)) {
          return Collections.emptyList();
        }
      }

      return List.of(new BuildOnSaveInfo(context));
    }

    @Override
    public Collection<String> getSearchableOptions() {
      return List.of(JavaCompilerBundle.message("settings.actions.on.save.page.build.project.on.save.checkbox"));
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
