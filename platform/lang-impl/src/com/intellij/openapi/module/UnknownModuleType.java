// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @NotNull ModuleBuilder createModuleBuilder() {
    return myModuleType.createModuleBuilder();
  }

  @SuppressWarnings("DialogTitleCapitalization")
  @Override
  public @NotNull String getName() {
    return ProjectBundle.message("module.type.unknown.name", myModuleType.getName());
  }

  @Override
  public @NotNull String getDescription() {
    return myModuleType.getDescription();
  }

  @Override
  public @NotNull Icon getNodeIcon(boolean isOpened) {
    return myModuleType.getIcon();
  }

  @Override
  public ModuleWizardStep @NotNull [] createWizardSteps(final @NotNull WizardContext wizardContext, final @NotNull ModuleBuilder moduleBuilder, final @NotNull ModulesProvider modulesProvider) {
    return myModuleType.createWizardSteps(wizardContext, moduleBuilder, modulesProvider);
  }

}
