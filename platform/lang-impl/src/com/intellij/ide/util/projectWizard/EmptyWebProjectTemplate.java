// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectGeneratorPeer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.ide.projectWizard.NewProjectWizardConstants.Generators;

public class EmptyWebProjectTemplate extends WebProjectTemplate<Object> {

  @Override
  public String getId() {
    return Generators.EMPTY_WEB_PROJECT;
  }

  @Override
  public @Nls @NotNull String getName() {
    return ProjectBundle.message("item.text.empty.project");
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public void generateProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Object settings, @NotNull Module module) {
    //ignore
  }

  @Override
  public @NotNull ProjectGeneratorPeer<Object> createPeer() {
    return new ProjectGeneratorPeer<>() {
      @Override
      public @NotNull JComponent getComponent() {
        return new JPanel();
      }

      @Override
      public void buildUI(@NotNull SettingsStep settingsStep) {
        settingsStep.addSettingsComponent(getComponent());
      }

      @Override
      public @NotNull Object getSettings() {
        return new Object();
      }

      @Override
      public @Nullable ValidationInfo validate() {
        return null;
      }

      @Override
      public boolean isBackgroundJobRunning() {
        return false;
      }

      @Override
      public void addSettingsStateListener(@NotNull SettingsStateListener listener) {
      }
    };
  }
}
