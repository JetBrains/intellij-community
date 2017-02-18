/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.facet.impl.statistics;

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ModuleTypeUsagesCollector extends AbstractApplicationUsagesCollector {
  public static final String GROUP_ID = "module type";

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID, GroupDescriptor.HIGHER_PRIORITY);
  }


  @Override
  @NotNull
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) {
    Set<String> modulesTypes = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      modulesTypes.add(ModuleType.get(module).getId());
    }
    return ContainerUtil.map2Set(modulesTypes, moduleType -> new UsageDescriptor(moduleType, 1));
  }
}
