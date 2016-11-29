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
package com.intellij.ide.projectWizard;

import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class ProjectCategoryUsagesCollector extends UsagesCollector {

  private final FactoryMap<String, UsageDescriptor> myUsageDescriptors = new FactoryMap<String, UsageDescriptor>() {
    @Nullable
    @Override
    protected UsageDescriptor create(String key) {
      return new UsageDescriptor(key, 0);
    }
  };

  public static void projectTypeUsed(@NotNull String projectTypeId) {
    getUsageDescriptors().get("project.category." + projectTypeId).advance();
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    HashSet<UsageDescriptor> descriptors = new HashSet<>();
    descriptors.addAll(getUsageDescriptors().values());
    getUsageDescriptors().clear();
    return descriptors;
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("Project Category");
  }

  private static FactoryMap<String, UsageDescriptor> getUsageDescriptors() {
    return ServiceManager.getService(ProjectCategoryUsagesCollector.class).myUsageDescriptors;
  }
}
