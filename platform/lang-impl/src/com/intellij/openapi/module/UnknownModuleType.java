/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;

import javax.swing.*;


public class UnknownModuleType extends ModuleType {
  private final ModuleType myModuleType;

  public UnknownModuleType(String id, ModuleType moduleType) {
    super(id);
    myModuleType = moduleType;
  }

  public ModuleBuilder createModuleBuilder() {
    return myModuleType.createModuleBuilder();
  }

  public String getName() {
    return ProjectBundle.message("module.type.unknown.name", myModuleType.getName());
  }

  public String getDescription() {
    return myModuleType.getDescription();
  }

  public Icon getBigIcon() {
    return myModuleType.getBigIcon();
  }

  public Icon getNodeIcon(boolean isOpened) {
    return myModuleType.getNodeIcon(isOpened);
  }

  public ModuleWizardStep[] createWizardSteps(final WizardContext wizardContext, final ModuleBuilder moduleBuilder, final ModulesProvider modulesProvider) {
    return myModuleType.createWizardSteps(wizardContext, moduleBuilder, modulesProvider);
  }

}