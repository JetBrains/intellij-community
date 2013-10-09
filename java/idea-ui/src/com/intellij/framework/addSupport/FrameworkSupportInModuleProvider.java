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
package com.intellij.framework.addSupport;

import com.intellij.framework.FrameworkOrGroup;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.framework.FrameworkTypeEx;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
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

  public String getPresentableName() {
    return getFrameworkType().getPresentableName();
  }

  @NotNull
  public String[] getProjectCategories() {
    return getFrameworkType().getProjectCategories();
  }

  public FrameworkRole[] getRoles() {
    return getFrameworkType().getRoles();
  }

  public String getVersionLabel() {
    return "Version:";
  }

  public List<String> getOptionalDependenciesFrameworkIds() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public String getId() {
    return getFrameworkType().getId();
  }

  @Override
  public Icon getIcon() {
    return getFrameworkType().getIcon();
  }

  @Override
  public String toString() {
    return getPresentableName();
  }
}
