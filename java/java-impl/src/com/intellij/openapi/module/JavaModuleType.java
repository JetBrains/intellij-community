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
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.util.ArrayList;

public class JavaModuleType extends ModuleType<JavaModuleBuilder> {

  public static ModuleType getModuleType() {
    return ModuleTypeManager.getInstance().findByID(JAVA_MODULE);
  }

  public static final String MODULE_NAME = ProjectBundle.message("module.type.java.name");
  public static final String JAVA_GROUP = "Java";
  private static final String JAVA_MODULE = ModuleTypeId.JAVA_MODULE;

  public JavaModuleType() {
    this(JAVA_MODULE);
  }

  protected JavaModuleType(@NonNls String id) {
    super(id);
  }

  @NotNull
  @Override
  public JavaModuleBuilder createModuleBuilder() {
    return new JavaModuleBuilder();
  }

  @NotNull
  @Override
  public String getName() {
    return MODULE_NAME;
  }

  @NotNull
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

  @NotNull
  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull final WizardContext wizardContext, @NotNull final JavaModuleBuilder moduleBuilder,
                                              @NotNull final ModulesProvider modulesProvider) {
    final ProjectWizardStepFactory wizardFactory = ProjectWizardStepFactory.getInstance();
    ArrayList<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    if (!wizardContext.isNewWizard()) {
      final ModuleWizardStep supportForFrameworksStep = wizardFactory.createSupportForFrameworksStep(wizardContext, moduleBuilder, modulesProvider);
      if (supportForFrameworksStep != null) {
        steps.add(supportForFrameworksStep);
      }
    }
    final ModuleWizardStep[] wizardSteps = steps.toArray(new ModuleWizardStep[steps.size()]);
    return ArrayUtil.mergeArrays(wizardSteps, super.createWizardSteps(wizardContext, moduleBuilder, modulesProvider));
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep, @NotNull final ModuleBuilder moduleBuilder) {
    return ProjectWizardStepFactory.getInstance().createJavaSettingsStep(settingsStep, moduleBuilder, new Condition<SdkTypeId>() {
      @Override
      public boolean value(SdkTypeId sdkType) {
        return moduleBuilder.isSuitableSdkType(sdkType);
      }
    });
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
  public boolean isValidSdk(@NotNull final Module module, final Sdk projectSdk) {
    return isValidJavaSdk(module);
  }

  public static boolean isValidJavaSdk(@NotNull Module module) {
    if (ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.SOURCES).isEmpty()) return true;
    return JavaPsiFacade.getInstance(module.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT,
                                                                    module.getModuleWithLibrariesScope()) != null;
  }
}
