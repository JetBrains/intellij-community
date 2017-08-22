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

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.internal.statistic.AbstractProjectsUsagesCollector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class FrameworkUsagesCollector extends AbstractProjectsUsagesCollector {
  public static final String GROUP_ID = "frameworks";

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID, GroupDescriptor.HIGHER_PRIORITY);
  }


  @Override
  @NotNull
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) {
    final Set<String> facets = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
        facets.add(facet.getType().getStringId());
      }
    }

    return ContainerUtil.map2Set(facets, facet -> new UsageDescriptor(facet, 1));
  }
}
