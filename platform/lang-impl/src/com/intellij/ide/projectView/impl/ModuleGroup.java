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

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ModuleGroup {
  public static final DataKey<ModuleGroup[]> ARRAY_DATA_KEY = DataKey.create("moduleGroup.array");

  private final String[] myGroupPath;

  public ModuleGroup(@NotNull String[] groupPath) {
    myGroupPath = groupPath;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleGroup)) return false;

    final ModuleGroup moduleGroup = (ModuleGroup)o;

    if (!Arrays.equals(myGroupPath, moduleGroup.myGroupPath)) return false;

    return true;
  }

  public int hashCode() {
    return myGroupPath[myGroupPath.length-1].hashCode();
  }

  public String[] getGroupPath() {
    return myGroupPath;
  }

  @NotNull
  public Collection<Module> modulesInGroup(Project project, boolean recursively) {
    final Module[] allModules = ModuleManager.getInstance(project).getModules();
    List<Module> result = new ArrayList<>();
    for (final Module module : allModules) {
      String[] group = ModuleManager.getInstance(project).getModuleGroupPath(module);
      if (group == null) continue;
      if (Arrays.equals(myGroupPath, group) || (recursively && isChild(myGroupPath, group))) {
        result.add(module);
      }
    }
    return result;
  }

  public Collection<ModuleGroup> childGroups(Project project) {
    return childGroups(null, project);
  }

  public Collection<ModuleGroup> childGroups(DataContext dataContext) {
    return childGroups(LangDataKeys.MODIFIABLE_MODULE_MODEL.getData(dataContext), CommonDataKeys.PROJECT.getData(dataContext));
  }

  public Collection<ModuleGroup> childGroups(ModifiableModuleModel model, Project project) {
    final Module[] allModules;
    if ( model != null ) {
      allModules = model.getModules();
    } else {
      allModules = ModuleManager.getInstance(project).getModules();
    }

    Set<ModuleGroup> result = new THashSet<>();
    for (Module module : allModules) {
      String[] group;
      if ( model != null ) {
        group = model.getModuleGroupPath(module);
      } else {
        group = ModuleManager.getInstance(project).getModuleGroupPath(module);
      }
      if (group == null) continue;
      final String[] directChild = directChild(myGroupPath, group);
      if (directChild != null) {
        result.add(new ModuleGroup(directChild));
      }
    }

    return result;
  }

  private static boolean isChild(final String[] parent, final String[] descendant) {
    if (parent.length >= descendant.length) return false;
    for (int i = 0; i < parent.length; i++) {
      String group = parent[i];
      if (!group.equals(descendant[i])) return false;
    }
    return true;
  }

  private static String[] directChild(final String[] parent, final String[] descendant) {
    if (!isChild(parent, descendant)) return null;
    return ArrayUtil.append(parent, descendant[parent.length]);
  }

  public String presentableText() {
    return "'" + myGroupPath[myGroupPath.length - 1] + "'";
  }

  public String toString() {
    return myGroupPath[myGroupPath.length - 1];
  }
}
