// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public final class UnknownModuleType extends ModuleType {
  private final ModuleType myModuleType;

  public UnknownModuleType(String id, @NotNull ModuleType moduleType) {
    super(id);
    myModuleType = moduleType;
  }

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return myModuleType.createModuleBuilder();
  }

  @SuppressWarnings("DialogTitleCapitalization")
  @NotNull
  @Override
  public String getName() {
    return ProjectBundle.message("module.type.unknown.name", myModuleType.getName());
  }

  @NotNull
  @Override
  public String getDescription() {
    return myModuleType.getDescription();
  }

  @NotNull
  @Override
  public Icon getNodeIcon(boolean isOpened) {
    return myModuleType.getIcon();
  }

  @Override
  public ModuleWizardStep @NotNull [] createWizardSteps(@NotNull final WizardContext wizardContext, @NotNull final ModuleBuilder moduleBuilder, @NotNull final ModulesProvider modulesProvider) {
    return myModuleType.createWizardSteps(wizardContext, moduleBuilder, modulesProvider);
  }

}
