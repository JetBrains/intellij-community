// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Nls
  @NotNull
  @Override
  public String getName() {
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

  @NotNull
  @Override
  public ProjectGeneratorPeer<Object> createPeer() {
    return new ProjectGeneratorPeer<>() {
      @NotNull
      @Override
      public JComponent getComponent() {
        return new JPanel();
      }

      @Override
      public void buildUI(@NotNull SettingsStep settingsStep) {
        settingsStep.addSettingsComponent(getComponent());
      }

      @NotNull
      @Override
      public Object getSettings() {
        return new Object();
      }

      @Nullable
      @Override
      public ValidationInfo validate() {
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
