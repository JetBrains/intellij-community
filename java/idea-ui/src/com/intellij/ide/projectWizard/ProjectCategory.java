/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class ProjectCategory {

  public static final ExtensionPointName<ProjectCategory> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.projectWizard.projectCategory");

  @NotNull
  public abstract ModuleBuilder createModuleBuilder();

  public String getId() {
    return createModuleBuilder().getBuilderId();
  }

  public String getDisplayName() {
    return createModuleBuilder().getPresentableName();
  }

  public Icon getIcon() {
    return createModuleBuilder().getNodeIcon();
  }

  public String getDescription() {
    return createModuleBuilder().getDescription();
  }

  public String getGroupName() {
    return createModuleBuilder().getGroupName();
  }

  public FrameworkRole[] getAcceptableFrameworkRoles() {
    return new FrameworkRole[] {createModuleBuilder().getDefaultAcceptableRole()};
  }

  /**
   * Describes "main" frameworks to be shown on top of the tree
   */
  @NotNull
  public String[] getAssociatedFrameworkIds() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  /**
   * Preselects the frameworks in tree.
   */
  public String[] getPreselectedFrameworkIds() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public int getWeight() {
    return 0;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }
}
