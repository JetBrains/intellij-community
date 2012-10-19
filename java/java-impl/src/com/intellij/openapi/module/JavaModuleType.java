/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

public class JavaModuleType extends ModuleType<JavaModuleBuilder> {

  public static ModuleType getModuleType() {
    return ModuleTypeManager.getInstance().findByID(JAVA_MODULE);
  }

  private static final String JAVA_MODULE = "JAVA_MODULE";

  public JavaModuleType() {
    this(JAVA_MODULE);
  }

  protected JavaModuleType(@NonNls String id) {
    super(id);
  }

  @Override
  public JavaModuleBuilder createModuleBuilder() {
    return new JavaModuleBuilder();
  }

  @Override
  public String getName() {
    return ProjectBundle.message("module.type.java.name");
  }

  @Override
  public String getDescription() {
    return ProjectBundle.message("module.type.java.description");
  }

  @Override
  public Icon getBigIcon() {
    return getJavaModuleIcon();
  }

  @Override
  public Icon getNodeIcon(boolean isOpened) {
    return getJavaModuleNodeIconClosed();
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(final WizardContext wizardContext, final JavaModuleBuilder moduleBuilder,
                                              final ModulesProvider modulesProvider) {
    final ProjectWizardStepFactory wizardFactory = ProjectWizardStepFactory.getInstance();
    ArrayList<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    steps.add(wizardFactory.createSourcePathsStep(wizardContext, moduleBuilder, getWizardIcon(), "reference.dialogs.new.project.fromScratch.source"));
    steps.add(wizardFactory.createProjectJdkStep(wizardContext, JavaSdk.getInstance(), moduleBuilder, new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        //final Sdk projectJdk = wizardFactory.getNewProjectSdk(wizardContext);
        //return projectJdk == null || projectJdk.getSdkType() != JavaSdk.getInstance() ? Boolean.TRUE : Boolean.FALSE;
        return Boolean.TRUE;
      }
    }, getWizardIcon(), "reference.dialogs.new.project.fromScratch.sdk"));
    final ModuleWizardStep supportForFrameworksStep = wizardFactory.createSupportForFrameworksStep(wizardContext, moduleBuilder, modulesProvider);
    if (supportForFrameworksStep != null) {
      steps.add(supportForFrameworksStep);
    }
    final ModuleWizardStep[] wizardSteps = steps.toArray(new ModuleWizardStep[steps.size()]);
    return ArrayUtil.mergeArrays(wizardSteps, super.createWizardSteps(wizardContext, moduleBuilder, modulesProvider));
  }

  @Nullable
  @Override
  public ModuleWizardStep createSettingsStep(WizardContext context) {
    return null; //ProjectWizardStepFactory.getInstance().createNameAndLocationStep(context);
  }

  private static Icon getJavaModuleIcon() {
    return AllIcons.Modules.Types.JavaModule;
  }

  private static Icon getJavaModuleNodeIconClosed() {
    return AllIcons.Nodes.Module;
  }

  private static class WizardIconHolder {
    private static final Icon WIZARD_ICON = IconLoader.getIcon("/addmodulewizard.png");
  }

  private static Icon getWizardIcon() {

    return WizardIconHolder.WIZARD_ICON;
  }

  @Override
  public boolean isValidSdk(final Module module, final Sdk projectSdk) {
    return isValidJavaSdk(module);
  }

  public static boolean isValidJavaSdk(final Module module) {
    if (ModuleRootManager.getInstance(module).getSourceRoots().length == 0) return true;
    return JavaPsiFacade.getInstance(module.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, 
                                                                    module.getModuleWithLibrariesScope()) != null;
  }
}
