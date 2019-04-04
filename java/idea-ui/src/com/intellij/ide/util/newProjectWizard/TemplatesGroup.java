/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.projectWizard.ProjectCategory;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * The groups are shown in "Project Type" selection list.
 * @author Dmitry Avdeev
 */
public class TemplatesGroup implements Comparable<TemplatesGroup> {

  private final String myName;
  private final String myDescription;
  private final Icon myIcon;
  private final int myWeight;
  private final String myParentGroup;
  private final String myId;
  private final ModuleBuilder myModuleBuilder;
  private ProjectCategory myProjectCategory;

  public TemplatesGroup(String name, String description, Icon icon, int weight, String parentGroup, String id, ModuleBuilder moduleBuilder) {
    myName = name;
    myDescription = description;
    myIcon = icon;
    myWeight = weight;
    myParentGroup = parentGroup;
    myId = id;
    myModuleBuilder = moduleBuilder;
  }

  /**
   * Category-based group
   * @param category
   */
  public TemplatesGroup(ProjectCategory category) {
    this(category.getDisplayName(), category.getDescription(), category.getIcon(), category.getWeight(), category.getGroupName(), category.getId(), category.createModuleBuilder());
    myProjectCategory = category;
  }

  public TemplatesGroup(ModuleBuilder builder) {
    this(builder.getPresentableName(), builder.getDescription(), builder.getNodeIcon(), builder.getWeight(), builder.getParentGroup(), builder.getBuilderId(), builder);
  }

  @Nullable
  public ModuleBuilder getModuleBuilder() {
    return myModuleBuilder;
  }

  public ProjectCategory getProjectCategory() { return myProjectCategory; }

  public String getName() {
    return myName;
  }

  public String getDescription() {
    return myDescription;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TemplatesGroup group = (TemplatesGroup)o;

    if (!myName.equals(group.myName)) return false;

    return true;
  }

  public int getWeight() {
    return myWeight;
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public int compareTo(@NotNull TemplatesGroup o) {
    int i = o.myWeight - myWeight;
    if (i != 0) return i;
    int i1 = Comparing.compare(o.getParentGroup(), getParentGroup());
    if (i1 != 0) return i1;
    return o.getName().compareTo(getName());
  }

  public String getParentGroup() {
    return myParentGroup;
  }

  public String getId() {
    return myId;
  }
}
