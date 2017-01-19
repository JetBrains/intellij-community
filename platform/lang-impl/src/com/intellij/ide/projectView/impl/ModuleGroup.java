/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ModuleGroup {
  public static final DataKey<ModuleGroup[]> ARRAY_DATA_KEY = DataKey.create("moduleGroup.array");
  private final List<String> myGroupPath;

  public ModuleGroup(@NotNull List<String> groupPath) {
    myGroupPath = groupPath;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleGroup)) return false;

    return myGroupPath.equals(((ModuleGroup)o).myGroupPath);
  }

  public int hashCode() {
    return myGroupPath.hashCode();
  }

  public String[] getGroupPath() {
    return ArrayUtil.toStringArray(myGroupPath);
  }

  public List<String> getGroupPathList() {
    return myGroupPath;
  }

  @NotNull
  public Collection<Module> modulesInGroup(Project project, boolean recursively) {
    return modulesInGroup(ModuleGrouper.instanceFor(project), recursively);
  }

  @NotNull
  public Collection<Module> modulesInGroup(ModuleGrouper grouper, boolean recursively) {
    List<Module> result = new ArrayList<>();
    for (final Module module : grouper.getAllModules()) {
      List<String> group = grouper.getGroupPath(module);
      if (myGroupPath.equals(group) || (recursively && isChild(myGroupPath, group))) {
        result.add(module);
      }
    }
    return result;
  }

  @NotNull
  public Collection<ModuleGroup> childGroups(ModuleGrouper grouper) {
    Set<ModuleGroup> result = new THashSet<>();
    for (Module module : grouper.getAllModules()) {
      List<String> group = grouper.getGroupPath(module);
      if (isChild(myGroupPath, group)) {
        final List<String> directChild = ContainerUtil.append(myGroupPath, group.get(myGroupPath.size()));
        result.add(new ModuleGroup(directChild));
      }
    }

    return result;
  }

  private static boolean isChild(final List<String> parent, final List<String> descendant) {
    return descendant.size() > parent.size() && descendant.subList(0, parent.size()).equals(parent);
  }

  public String presentableText() {
    return "'" + myGroupPath.get(myGroupPath.size() - 1) + "'";
  }

  public String toString() {
    return myGroupPath.get(myGroupPath.size() - 1);
  }
}
