/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;

import javax.swing.*;

/**
 * A factory for creating some commonly used project wizards steps
 */
public abstract class ProjectWizardStepFactory {

  public static ProjectWizardStepFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(ProjectWizardStepFactory.class);
  }

  public abstract ModuleWizardStep createNameAndLocationStep(WizardContext wizardContext, JavaModuleBuilder builder, ModulesProvider modulesProvider, Icon icon, String helpId);

  public abstract ModuleWizardStep createOutputPathPathsStep(ModuleWizardStep nameAndLocationStep, JavaModuleBuilder builder, Icon icon, String helpId);

  public abstract ModuleWizardStep createSourcePathsStep(ModuleWizardStep nameAndLocationStep, JavaModuleBuilder builder, Icon icon, String helpId);
}
