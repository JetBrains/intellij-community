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

import com.intellij.framework.FrameworkGroup;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 04.09.13
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

  @Nullable
  public String getParentId() {
    return null;
  }

  public FrameworkRole[] getAcceptableFrameworkRoles() {
    return new FrameworkRole[] {createModuleBuilder().getDefaultAcceptableRole()};
  }

  @Nullable
  public FrameworkGroup getAssociatedFrameworkGroup() {
    return null;
  }

  @NotNull
  public String[] getAssociatedFrameworkIds() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  private static Map<String, ProjectCategory> map = ContainerUtil.newMapFromValues(Arrays.asList(EXTENSION_POINT_NAME.getExtensions()).iterator(), new Convertor<ProjectCategory, String>() {
    @Override
    public String convert(ProjectCategory o) {
      return o.getId();
    }
  });

  public static ProjectCategory findById(String id) {
    return map.get(id);
  }
}
