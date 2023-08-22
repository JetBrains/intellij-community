// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.framework.addSupport;

import com.intellij.diagnostic.PluginException;
import com.intellij.framework.FrameworkOrGroup;
import com.intellij.framework.FrameworkTypeEx;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public abstract class FrameworkSupportInModuleProvider implements FrameworkOrGroup {

  @NotNull
  public abstract FrameworkTypeEx getFrameworkType();

  @NotNull
  public abstract FrameworkSupportInModuleConfigurable createConfigurable(@NotNull FrameworkSupportModel model);

  public abstract boolean isEnabledForModuleType(@NotNull ModuleType moduleType);

  public boolean isEnabledForModuleBuilder(@NotNull ModuleBuilder builder) {
    return isEnabledForModuleType(builder.getModuleType());
  }

  public boolean isSupportAlreadyAdded(@NotNull Module module, @NotNull FacetsProvider facetsProvider) {
    return false;
  }

  public boolean canAddSupport(@NotNull Module module, @NotNull FacetsProvider facetsProvider) {
    return !isSupportAlreadyAdded(module, facetsProvider);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return getFrameworkType().getPresentableName();
  }

  public FrameworkRole[] getRoles() {
    return getFrameworkType().getRoles();
  }

  public String getVersionLabel() {
    return "Version:";
  }

  public List<FrameworkDependency> getDependenciesFrameworkIds() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public String getId() {
    return getFrameworkType().getId();
  }

  @NotNull
  @Override
  public Icon getIcon() {
    Icon icon = getFrameworkType().getIcon();
    //noinspection ConstantConditions
    if (icon == null) {
      Class<?> aClass = getFrameworkType().getClass();
      Logger logger = Logger.getInstance(FrameworkSupportInModuleProvider.class);
      PluginException.logPluginError(logger, "FrameworkType::getIcon returns null for " + aClass, null, aClass);
      return EmptyIcon.ICON_16;
    }
    return icon;
  }

  @Override
  public String toString() {
    return getPresentableName();
  }

  public static final class FrameworkDependency {
    private final String myFrameworkId;
    private final boolean myOptional;

    private FrameworkDependency(String frameworkId, boolean optional) {
      myFrameworkId = frameworkId;
      myOptional = optional;
    }

    public static FrameworkDependency optional(String frameworkId) {
      return new FrameworkDependency(frameworkId, true);
    }

    public static FrameworkDependency required(String frameworkId) {
      return new FrameworkDependency(frameworkId, false);
    }

    public String getFrameworkId() {
      return myFrameworkId;
    }

    /**
     * True if dependency is not required.
     */
    public boolean isOptional() {
      return myOptional;
    }
  }
}
