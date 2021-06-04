// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.options;

import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.ide.actionsOnSave.*;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.ActionLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CompilerConfigurable implements SearchableConfigurable.Parent, Configurable.NoScroll {
  private static final String CONFIGURABLE_ID = "project.propCompiler";

  private final CompilerUIConfigurable myCompilerUIConfigurable;

  public CompilerConfigurable(Project project) {
    myCompilerUIConfigurable = new CompilerUIConfigurable(project);
  }

  @Override
  public String getDisplayName() {
    return JavaCompilerBundle.message("compiler.configurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "project.propCompiler";
  }

  @Override
  @NotNull
  public String getId() {
    return CONFIGURABLE_ID;
  }

  @Override
  public JComponent createComponent() {
    return myCompilerUIConfigurable.createComponent();
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public boolean isModified() {
    return myCompilerUIConfigurable.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myCompilerUIConfigurable.apply();
  }

  @Override
  public void reset() {
    myCompilerUIConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myCompilerUIConfigurable.disposeUIResources();
  }

  @Override
  public Configurable @NotNull [] getConfigurables() {
    return new Configurable[0];
  }


  public static class BuildOnSaveInfoProvider extends ActionOnSaveInfoProvider {
    @Override
    protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull Project project) {
      for (CompilerOptionsFilter filter : CompilerOptionsFilter.EP_NAME.getExtensionList()) {
        if (!filter.isAvailable(CompilerOptionsFilter.Setting.AUTO_MAKE, project)) return Collections.emptyList();
      }

      return List.of(new BuildOnSaveInfo(project));
    }
  }


  private static class BuildOnSaveInfo extends ActionOnSaveBackedByOwnConfigurable<CompilerConfigurable> {
    private final @NotNull Project myProject;

    private BuildOnSaveInfo(@NotNull Project project) {
      super(CONFIGURABLE_ID, CompilerConfigurable.class);
      myProject = project;
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
      return CompilerWorkspaceConfiguration.getInstance(myProject).MAKE_PROJECT_ON_SAVE;
    }

    @Override
    protected boolean isActionOnSaveEnabledAccordingToUiState(@NotNull CompilerConfigurable configurable) {
      return configurable.myCompilerUIConfigurable.getBuildOnSaveCheckBox().isSelected();
    }

    @Override
    protected void setActionOnSaveEnabled(@NotNull CompilerConfigurable configurable, boolean enabled) {
      configurable.myCompilerUIConfigurable.getBuildOnSaveCheckBox().setSelected(enabled);
    }

    @Override
    public @NotNull List<? extends ActionLink> getActionLinks() {
      String linkText = JavaCompilerBundle.message("settings.actions.on.save.page.compiler.settings.link");
      return List.of(ActionsOnSaveConfigurable.createGoToPageInSettingsLink(linkText, CONFIGURABLE_ID));
    }
  }
}
