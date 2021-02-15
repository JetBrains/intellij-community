// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class ModuleGroup {
  public static final DataKey<ModuleGroup[]> ARRAY_DATA_KEY = DataKey.create("moduleGroup.array");
  private final List<String> myGroupPath;

  public ModuleGroup(@NotNull List<String> groupPath) {
    myGroupPath = groupPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleGroup)) return false;

    return myGroupPath.equals(((ModuleGroup)o).myGroupPath);
  }

  @Override
  public int hashCode() {
    return myGroupPath.hashCode();
  }

  public String @NotNull [] getGroupPath() {
    return ArrayUtilRt.toStringArray(myGroupPath);
  }

  @NotNull
  public List<String> getGroupPathList() {
    return myGroupPath;
  }

  @NotNull
  public Collection<Module> modulesInGroup(@NotNull Project project, boolean recursively) {
    return modulesInGroup(ModuleGrouper.instanceFor(project), recursively);
  }

  /**
   * Returns modules in this group (without modules in sub-groups) using cache built for default project grouper.
   */
  @NotNull
  public Collection<Module> modulesInGroup(@NotNull Project project) {
    return ModuleGroupsTree.getModuleGroupTree(project).getModulesInGroup(this);
  }

  @NotNull
  public Collection<Module> modulesInGroup(@NotNull ModuleGrouper grouper, boolean recursively) {
    List<Module> result = new ArrayList<>();
    Set<List<String>> moduleAsGroupsPaths = ContainerUtil.map2Set(grouper.getAllModules(), module -> grouper.getModuleAsGroupPath(module));
    for (final Module module : grouper.getAllModules()) {
      List<String> group = grouper.getGroupPath(module);
      if (myGroupPath.equals(group) || isChild(myGroupPath, group) && (recursively || isUnderGroupWithSameNameAsSomeModule(myGroupPath, group, moduleAsGroupsPaths))) {
        result.add(module);
      }
    }
    return result;
  }

  private static boolean isUnderGroupWithSameNameAsSomeModule(@NotNull List<String> parent, @NotNull List<String> descendant, @NotNull Set<List<String>> moduleNamesAsGroups) {
    return descendant.size() > parent.size() && moduleNamesAsGroups.contains(descendant.subList(0, parent.size() + 1));
  }

  /**
   * Returns direct subgroups of this group using cache built for default project grouper.
   */
  @NotNull
  public Collection<ModuleGroup> childGroups(@NotNull Project project) {
    return ModuleGroupsTree.getModuleGroupTree(project).getChildGroups(this);
  }

  @NotNull
  public Collection<ModuleGroup> childGroups(@NotNull ModuleGrouper grouper) {
    Set<ModuleGroup> result = new HashSet<>();
    Set<List<String>> moduleAsGroupsPaths = ContainerUtil.map2Set(grouper.getAllModules(), module -> grouper.getModuleAsGroupPath(module));
    for (Module module : grouper.getAllModules()) {
      List<String> group = grouper.getGroupPath(module);
      if (isChild(myGroupPath, group)) {
        final List<String> directChild = ContainerUtil.append(myGroupPath, group.get(myGroupPath.size()));
        if (!moduleAsGroupsPaths.contains(directChild)) {
          result.add(new ModuleGroup(directChild));
        }
      }
    }

    return result;
  }

  private static boolean isChild(@NotNull List<String> parent, @NotNull List<String> descendant) {
    return descendant.size() > parent.size() && descendant.subList(0, parent.size()).equals(parent);
  }

  @NotNull
  public @NlsSafe String presentableText() {
    return "'" + myGroupPath.get(myGroupPath.size() - 1) + "'";
  }

  @NotNull
  public @NlsSafe String getQualifiedName() {
    return StringUtil.join(myGroupPath, ".");
  }

  @Override
  public @NlsSafe String toString() {
    return myGroupPath.get(myGroupPath.size() - 1);
  }
}
