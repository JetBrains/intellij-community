/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.util.Factory;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
public interface RunnerAndConfigurationSettings {
  @Nullable
  ConfigurationFactory getFactory();

  boolean isTemplate();

  boolean isTemporary();

  RunConfiguration getConfiguration();

  void setName(String name);

  String getName();

  RunnerSettings getRunnerSettings(ProgramRunner runner);

  ConfigurationPerRunnerSettings getConfigurationSettings(ProgramRunner runner);

  @Nullable
  ConfigurationType getType();

  void checkSettings() throws RuntimeConfigurationException;

  void checkSettings(@Nullable Executor executor) throws RuntimeConfigurationException;

  void setTemporary(boolean temporary);

  Factory<RunnerAndConfigurationSettings> createFactory();
}
