// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ArrayUtilRt;
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
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  /**
   * Preselects the frameworks in tree.
   */
  public String[] getPreselectedFrameworkIds() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  public int getWeight() {
    return 0;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }
}
