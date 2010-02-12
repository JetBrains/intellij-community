/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A factory for creating some commonly used project wizards steps
 */
public abstract class ProjectWizardStepFactory {

  public static ProjectWizardStepFactory getInstance() {
    return ServiceManager.getService(ProjectWizardStepFactory.class);
  }
  /**
   * @deprecated
   */
  public abstract ModuleWizardStep createNameAndLocationStep(WizardContext wizardContext, JavaModuleBuilder builder, ModulesProvider modulesProvider, Icon icon, @NonNls String helpId);

  public abstract ModuleWizardStep createNameAndLocationStep(WizardContext wizardContext);

  public abstract ModuleWizardStep createOutputPathPathsStep(ModuleWizardStep nameAndLocationStep, JavaModuleBuilder builder, Icon icon, @NonNls String helpId);

  /**
   * @deprecated Use another version of this method:
   * @see com.intellij.ide.util.projectWizard.ProjectWizardStepFactory#createSourcePathsStep(WizardContext, SourcePathsBuilder, javax.swing.Icon, String) 
   */
  public abstract ModuleWizardStep createSourcePathsStep(ModuleWizardStep nameAndLocationStep, SourcePathsBuilder builder, Icon icon, @NonNls String helpId);

  public abstract ModuleWizardStep createSourcePathsStep(WizardContext context, SourcePathsBuilder builder, Icon icon, @NonNls String helpId);

  /**
   * @deprecated
   */
  public abstract ModuleWizardStep createProjectJdkStep(WizardContext context, JavaModuleBuilder builder, Computable<Boolean> isVisibile, Icon icon, @NonNls String helpId);

  public abstract ModuleWizardStep createProjectJdkStep(WizardContext context, SdkType type, JavaModuleBuilder builder, Computable<Boolean> isVisibile, Icon icon, @NonNls String helpId);

  public abstract ModuleWizardStep createProjectJdkStep(final WizardContext wizardContext);

  @Nullable
  public abstract Sdk getNewProjectSdk(WizardContext wizardContext);

  @Nullable
  public abstract ModuleWizardStep createSupportForFrameworksStep(WizardContext context, ModuleBuilder builder);
}
