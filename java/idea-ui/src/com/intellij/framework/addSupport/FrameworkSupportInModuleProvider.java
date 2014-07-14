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
import com.intellij.framework.FrameworkTypeEx;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
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

  @Override
  public Icon getIcon() {
    return getFrameworkType().getIcon();
  }

  @Override
  public String toString() {
    return getPresentableName();
  }

  public static class FrameworkDependency {
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
