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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FreezeLogger {
  
  private static final Logger LOG = Logger.getInstance(FreezeLogger.class);
  private static final Alarm ALARM = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private static final int MAX_ALLOWED_TIME = 500;
  
  public static void runUnderPerformanceMonitor(@Nullable Project project, @NotNull Runnable action) {
    final ModalityState initial = ModalityState.current();
    ALARM.cancelAllRequests();
    ALARM.addRequest(() -> dumpThreads(project, initial), MAX_ALLOWED_TIME);
    
    try {
      action.run();
    }
    finally {
      ALARM.cancelAllRequests();
    }
  }
  
  private static void dumpThreads(@Nullable Project project, @NotNull ModalityState initialState) {
    if (!initialState.equals(ModalityState.current())) {
      return;
    }
    
    final String edtTrace = ThreadDumper.dumpEdtStackTrace();
    if (edtTrace.contains("java.lang.ClassLoader.loadClass")) {
      return;
    }

    final boolean isInDumbMode = project != null && !project.isDisposed() && DumbService.isDumb(project);
    final String dumps = ThreadDumper.dumpThreadsToString();
    final String msg = "Typing freeze report, (DumbMode=" + isInDumbMode + ") thread dumps attached. EDT stacktrace:\n"
                 + edtTrace
                 + "\n\n\n";
    
    LOG.error(msg, dumps);
  }
  
}