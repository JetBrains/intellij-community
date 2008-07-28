package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.LogFileOptions;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * User: anna
 * Date: 01-Feb-2006
 */
public class LogFilesManager {
  private final Map<LogFileOptions, Set<String>> myLogFileManagerMap = new LinkedHashMap<LogFileOptions, Set<String>>();
  private final Map<LogFileOptions, RunConfigurationBase> myLogFileToConfiguration = new HashMap<LogFileOptions, RunConfigurationBase>();
  private final Runnable myUpdateRequest;
  private final LogConsoleManager myManager;
  private Alarm myUpdateAlarm = new Alarm();

  public LogFilesManager(final Project project, LogConsoleManager manager) {
    myManager = manager;
    myUpdateRequest = new Runnable() {
      public void run() {
        if (project.isDisposed()) return;
        if (myUpdateAlarm == null) return; //already disposed
        myUpdateAlarm.cancelAllRequests();
        for (LogFileOptions logFile : myLogFileManagerMap.keySet()) {
          final Set<String> oldFiles = myLogFileManagerMap.get(logFile);
          final Set<String> newFiles = logFile.getPaths();
          addConfigurationConsoles(logFile, new Condition<String>(){
            public boolean value(final String file) {
              return !oldFiles.contains(file);
            }
          });
          for (String oldFile : oldFiles) {
            if (!newFiles.contains(oldFile)){
              myManager.removeLogConsole(oldFile);
            }
          }
          oldFiles.clear();
          oldFiles.addAll(newFiles);
        }
        myUpdateAlarm.addRequest(myUpdateRequest, 300, ModalityState.NON_MODAL);
      }
    };
  }

  public void registerFileMatcher(final RunConfigurationBase runConfiguration){
    final ArrayList<LogFileOptions> logFiles = runConfiguration.getAllLogFiles();
    for (LogFileOptions logFile : logFiles) {
      if (logFile.isEnabled()) {
        myLogFileManagerMap.put(logFile, logFile.getPaths());
        myLogFileToConfiguration.put(logFile, runConfiguration);
      }
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        Alarm updateAlarm = myUpdateAlarm;
        if (updateAlarm != null) {
          updateAlarm.addRequest(myUpdateRequest, 300, ModalityState.NON_MODAL);
        }
      }
    });
  }

  public void unregisterFileMatcher(){
    if (myUpdateAlarm != null) {
      myUpdateAlarm.cancelAllRequests();
      myUpdateAlarm = null;
    }
  }

  public void initLogConsoles(RunConfigurationBase base, ProcessHandler startedProcess) {
    final ArrayList<LogFileOptions> logFiles = base.getAllLogFiles();
    for (LogFileOptions logFile : logFiles) {
      if (logFile.isEnabled()) {
        addConfigurationConsoles(logFile, Condition.TRUE);
      }
    }
    base.createAdditionalTabComponents(myManager, startedProcess);
  }

  private void addConfigurationConsoles(final LogFileOptions logFile, Condition<String> shouldInclude) {
    final Set<String> paths = logFile.getPaths();
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
        myManager.addLogConsole(title, path, logFile.isSkipContent() ? new File(path).length() : 0);
      }
    }
  }
}
