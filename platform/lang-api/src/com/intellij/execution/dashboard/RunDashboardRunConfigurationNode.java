/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.dashboard;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author konstantin.aleev
 */
public interface RunDashboardRunConfigurationNode extends RunDashboardNode, UserDataHolder {
  @NotNull
  RunnerAndConfigurationSettings getConfigurationSettings();

  @NotNull
  List<RunDashboardCustomizer> getCustomizers();

  @NotNull
  RunDashboardRunConfigurationStatus getStatus();
}
