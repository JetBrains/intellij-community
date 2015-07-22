/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;

public abstract class ModuleType<T extends ModuleBuilder> {
  public static final ModuleType EMPTY;

  @NotNull
  private final String myId;
  private final FrameworkRole myFrameworkRole;

  protected ModuleType(@NotNull @NonNls String id) {
    myId = id;
    myFrameworkRole = new FrameworkRole(id);
  }

  @NotNull
  public abstract T createModuleBuilder();

  @NotNull
  public abstract String getName();
  @NotNull
  public abstract String getDescription();
  public abstract Icon getBigIcon();

  public Icon getIcon() {
    return getNodeIcon(false);
  }

  public abstract Icon getNodeIcon(@Deprecated boolean isOpened);

  @NotNull
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull T moduleBuilder, @NotNull ModulesProvider modulesProvider) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  @Nullable
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep, @NotNull ModuleBuilder moduleBuilder) {
    return null;
  }

  @Nullable
  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep, @NotNull ModuleBuilder moduleBuilder) {
    return null;
  }

  @NotNull
  public final String getId() {
    return myId;
  }

  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleType)) return false;

    final ModuleType moduleType = (ModuleType)o;

    return myId.equals(moduleType.myId);
  }

  public final int hashCode() {
    return myId.hashCode();
  }

  public String toString() {
    return getName();
  }

  static {
    EMPTY = instantiate("com.intellij.openapi.module.EmptyModuleType");
  }

  @NotNull
  private static ModuleType instantiate(String className) {
    try {
      return (ModuleType)Class.forName(className).newInstance();
    }
    catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public boolean isValidSdk(@NotNull Module module, @Nullable final Sdk projectSdk) {
    return true;
  }

  public static boolean is(@NotNull Module module, @NotNull ModuleType moduleType) {
    return moduleType.getId().equals(module.getOptionValue(Module.ELEMENT_TYPE));
  }

  @NotNull
  public static ModuleType get(@NotNull Module module) {
    final ModuleTypeManager instance = ModuleTypeManager.getInstance();
    if (instance == null) {
      return EMPTY;
    }
    return instance.findByID(module.getOptionValue(Module.ELEMENT_TYPE));
  }

  @NotNull
  public FrameworkRole getDefaultAcceptableRole() {
    return myFrameworkRole;
  }

  public boolean isSupportedRootType(JpsModuleSourceRootType type) {
    return true;
  }
}
