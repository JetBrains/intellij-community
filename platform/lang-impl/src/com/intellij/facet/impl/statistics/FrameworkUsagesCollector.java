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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class FrameworkUsagesCollector extends UsagesCollector {
  public static final String GROUP_ID = "frameworks";

  public static void persistProjectUsages(@NotNull Project project) {
    persistProjectUsages(project, getProjectUsages(project));
  }

  public static void persistProjectUsages(@NotNull Project project, @NotNull Set<UsageDescriptor> usages) {
    persistProjectUsages(project, usages, FrameworkStatisticsPersistenceComponent.getInstance());
  }

  public static void persistProjectUsages(@NotNull Project project,
                                          @NotNull Set<UsageDescriptor> usages,
                                          @NotNull FrameworkStatisticsPersistence persistence) {
    persistence.persistFrameworks(project, usages);
  }

  @NotNull
  public Set<UsageDescriptor> getApplicationUsages() {
    return getApplicationUsages(FrameworkStatisticsPersistenceComponent.getInstance());
  }

  @NotNull
  public Set<UsageDescriptor> getApplicationUsages(@NotNull final FrameworkStatisticsPersistence persistence) {
    final Map<String, Integer> facets = new HashMap<String, Integer>();
    
    for (Set<UsageDescriptor> frameworks : persistence.getFrameworks().values()) {
      for (UsageDescriptor framework : frameworks) {
        final String key = framework.getKey();
        final Integer count = facets.get(key);
        facets.put(key, count == null ? 1 : count.intValue() + 1);
      }
    }

    return ContainerUtil.map2Set(facets.entrySet(), new Function<Map.Entry<String, Integer>, UsageDescriptor>() {
      @Override
      public UsageDescriptor fun(Map.Entry<String, Integer> facet) {
        return new UsageDescriptor(getGroupDescriptor(), facet.getKey(), facet.getValue());
      }
    });
  }

  @NotNull
  @Override
  public String getGroupId() {
    return GROUP_ID;
  }

  public static GroupDescriptor getGroupDescriptor() {
    return GroupDescriptor.create(GROUP_ID, GroupDescriptor.HIGHER_PRIORITY);
  }

  @NotNull
  public Set<UsageDescriptor> getUsages(@Nullable Project project) {
    if (project != null) {
      persistProjectUsages(project, getProjectUsages(project));
    }

    return getApplicationUsages();
  }

  public static Set<UsageDescriptor> getProjectUsages(@NotNull Project project) {
    final Set<String> facets = new HashSet<String>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
        facets.add(facet.getType().getStringId());
      }
    }

    return ContainerUtil.map2Set(facets, new Function<String, UsageDescriptor>() {
      @Override
      public UsageDescriptor fun(String facet) {
        return new UsageDescriptor(getGroupDescriptor(), facet, 1);
      }
    });
  }
}
