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
package com.intellij.compiler;

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/11/13
 */
public class CompilerSettingsUsageCollector extends AbstractApplicationUsagesCollector{
  public static final String GROUP_ID = "compiler";

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@Nullable Project project) throws CollectUsagesException {
    final CompilerWorkspaceConfiguration wsConfig = CompilerWorkspaceConfiguration.getInstance(project);
    
    final Set<UsageDescriptor> result = new HashSet<>();
    if (wsConfig.MAKE_PROJECT_ON_SAVE) {
      result.add(new UsageDescriptor("auto_make", 1));
    }
    if (wsConfig.PARALLEL_COMPILATION) {
      result.add(new UsageDescriptor("compile_parallel", 1));
    }

    return result;
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);
  }
}
