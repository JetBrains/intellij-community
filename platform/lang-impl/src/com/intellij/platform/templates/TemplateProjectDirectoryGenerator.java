// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGeneratorBase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class TemplateProjectDirectoryGenerator<T> extends DirectoryProjectGeneratorBase<T> {
  private final LocalArchivedTemplate myTemplate;
  private final ModuleBuilder myModuleBuilder;

  public TemplateProjectDirectoryGenerator(@NotNull LocalArchivedTemplate template) {
    myTemplate = template;
    myModuleBuilder = myTemplate.createModuleBuilder();
  }

  @Override
  public @Nls @NotNull String getName() {
    return myTemplate.getName();
  }

  @Override
  public @Nullable Icon getLogo() {
    return myTemplate.getIcon();
  }

  @Override
  public void generateProject(@NotNull Project newProject,
                              @NotNull VirtualFile baseDir,
                              @NotNull T settings,
                              @NotNull Module module) {
    throw new IllegalStateException("Isn't supposed to be invoked, use generateProject(String, String) instead.");
  }

  public void generateProject(String name, String path){
    try {
      myModuleBuilder.createProject(name, path);
    }
    finally {
      myModuleBuilder.cleanup();
    }
  }

  @Override
  public @NotNull ValidationResult validate(@NotNull String baseDirPath) {
    String message = LangBundle.message("dialog.message.invalid.settings");
    for (WizardInputField field : myTemplate.getInputFields()) {
      try {
        if (field.validate()) {
          continue;
        }
      }
      catch (ConfigurationException e) {
        message = e.getMessage();
      }
      return new ValidationResult(message);
    }

    ValidationResult result = myTemplate.validate(baseDirPath);
    if(result != null){
      return result;
    }

    return ValidationResult.OK;
  }

  public void buildUI(@NotNull SettingsStep settingsStep){
    for (WizardInputField field : myTemplate.getInputFields()) {
      field.addToSettings(settingsStep);
    }

    if(myTemplate.getInputFields().isEmpty()){
      settingsStep.addSettingsComponent(new JLabel());
    }
  }
}
