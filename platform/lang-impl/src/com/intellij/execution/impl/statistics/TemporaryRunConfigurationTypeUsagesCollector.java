/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.impl.statistics;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Nikolay Matveev
 */
public class TemporaryRunConfigurationTypeUsagesCollector extends AbstractRunConfigurationTypeUsagesCollector {

  private static final GroupDescriptor GROUP_ID = GroupDescriptor.create("run-configuration-type-temp");

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GROUP_ID;
  }

  @Override
  protected boolean isApplicable(@NotNull RunManager runManager, @NotNull RunnerAndConfigurationSettings settings) {
    return settings.isTemporary();
  }
}
