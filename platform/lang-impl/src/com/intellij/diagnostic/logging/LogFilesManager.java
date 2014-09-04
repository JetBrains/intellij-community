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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.Alarm;
import com.intellij.util.SingleAlarm;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.*;

public class LogFilesManager {
  private final Map<LogFileOptions, Set<String>> myLogFileManagerMap = new LinkedHashMap<LogFileOptions, Set<String>>();
  private final LogConsoleManager myManager;
  private final SingleAlarm myUpdateAlarm;

  public LogFilesManager(@NotNull final Project project, @NotNull LogConsoleManager manager, @NotNull Disposable parentDisposable) {
    myManager = manager;

    myUpdateAlarm = new SingleAlarm(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) {
          return;
        }

        myUpdateAlarm.cancelAllRequests();
        for (final LogFileOptions logFile : myLogFileManagerMap.keySet()) {
          final Set<String> oldFiles = myLogFileManagerMap.get(logFile);
          final Set<String> newFiles = logFile.getPaths(); // should not be called in UI thread
          myLogFileManagerMap.put(logFile, newFiles);

          final Set<String> obsoleteFiles = new THashSet<String>(oldFiles);
          obsoleteFiles.removeAll(newFiles);

          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              if (project.isDisposed()) {
                return;
              }

              addConfigurationConsoles(logFile, new Condition<String>() {
                @Override
                public boolean value(final String file) {
                  return !oldFiles.contains(file);
                }
              }, newFiles);
              for (String each : obsoleteFiles) {
                myManager.removeLogConsole(each);
              }
              myUpdateAlarm.request();
            }
          });
        }
      }
    }, 500, Alarm.ThreadToUse.POOLED_THREAD, parentDisposable);
  }

  public void registerFileMatcher(@NotNull RunConfigurationBase runConfiguration) {
    final ArrayList<LogFileOptions> logFiles = runConfiguration.getAllLogFiles();
    for (LogFileOptions logFile : logFiles) {
      if (logFile.isEnabled()) {
        myLogFileManagerMap.put(logFile, logFile.getPaths());
      }
    }
    myUpdateAlarm.request();
  }

  public void initLogConsoles(@NotNull RunConfigurationBase base, ProcessHandler startedProcess) {
    List<LogFileOptions> logFiles = base.getAllLogFiles();
    for (LogFileOptions logFile : logFiles) {
      if (logFile.isEnabled()) {
        addConfigurationConsoles(logFile, Conditions.<String>alwaysTrue(), logFile.getPaths());
      }
    }
    base.createAdditionalTabComponents(myManager, startedProcess);
  }

  private void addConfigurationConsoles(final LogFileOptions logFile, Condition<String> shouldInclude, final Set<String> paths) {
    if (!paths.isEmpty()) {
      final TreeMap<String, String> title2Path = new TreeMap<String, String>();
      if (paths.size() == 1) {
        final String path = paths.iterator().next();
        if (shouldInclude.value(path)) {
          title2Path.put(logFile.getName(), path);
        }
      }
      else {
        for (String path : paths) {
          if (shouldInclude.value(path)) {
            String title = new File(path).getName();
            if (title2Path.containsKey(title)) {
              title = path;
            }
            title2Path.put(title, path);
          }
        }
      }
      for (final String title : title2Path.keySet()) {
        final String path = title2Path.get(title);
        assert path != null;
        myManager.addLogConsole(title, path, logFile.getCharset(), logFile.isSkipContent() ? new File(path).length() : 0);
      }
    }
  }
}
