/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.LogFileOptions;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;
import java.util.TreeMap;

public class LogFilesManager {
  private final LogConsoleManager myManager;

  public LogFilesManager(@NotNull LogConsoleManager manager) {
    myManager = manager;
  }

  public void addLogConsoles(@NotNull RunConfigurationBase runConfiguration, @Nullable ProcessHandler startedProcess) {
    for (LogFileOptions logFileOptions : runConfiguration.getAllLogFiles()) {
      if (logFileOptions.isEnabled()) {
        addConfigurationConsoles(logFileOptions, Conditions.<String>alwaysTrue(), logFileOptions.getPaths(), runConfiguration);
      }
    }
    runConfiguration.createAdditionalTabComponents(myManager, startedProcess);
  }

  private void addConfigurationConsoles(@NotNull LogFileOptions logFile, @NotNull Condition<String> shouldInclude, @NotNull Set<String> paths, @NotNull RunConfigurationBase runConfiguration) {
    if (paths.isEmpty()) {
      return;
    }

    TreeMap<String, String> titleToPath = new TreeMap<String, String>();
    if (paths.size() == 1) {
      String path = paths.iterator().next();
      if (shouldInclude.value(path)) {
        titleToPath.put(logFile.getName(), path);
      }
    }
    else {
      for (String path : paths) {
        if (shouldInclude.value(path)) {
          String title = new File(path).getName();
          if (titleToPath.containsKey(title)) {
            title = path;
          }
          titleToPath.put(title, path);
        }
      }
    }

    for (String title : titleToPath.keySet()) {
      String path = titleToPath.get(title);
      assert path != null;
      myManager.addLogConsole(title, path, logFile.getCharset(), logFile.isSkipContent() ? new File(path).length() : 0, runConfiguration);
    }
  }
}
