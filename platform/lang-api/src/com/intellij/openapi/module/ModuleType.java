// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    return moduleType.getId().equals(module.getModuleTypeName());
  }

  @NotNull
  public static ModuleType get(@NotNull Module module) {
    final ModuleTypeManager instance = ModuleTypeManager.getInstance();
    if (instance == null) {
      return EMPTY;
    }
    return instance.findByID(module.getModuleTypeName());
  }

  @NotNull
  public FrameworkRole getDefaultAcceptableRole() {
    return myFrameworkRole;
  }

  public boolean isSupportedRootType(JpsModuleSourceRootType type) {
    return true;
  }

  public boolean isMarkInnerSupportedFor(JpsModuleSourceRootType type) {
    return false;
  }
}
