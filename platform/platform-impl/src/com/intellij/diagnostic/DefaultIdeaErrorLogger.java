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
package com.intellij.diagnostic;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

/**
 * @author kir
 */
public class DefaultIdeaErrorLogger implements ErrorLogger {
  private static boolean ourOomOccured = false;
  @NonNls private static final String FATAL_ERROR_NOTIFICATION_PROPERTY = "idea.fatal.error.notification";
  @NonNls private static final String DISABLED_VALUE = "disabled";
  @NonNls private static final String ENABLED_VALUE = "enabled";
  @NonNls private static final String PARAM_PERMGEN = "PermGen";

  public boolean canHandle(IdeaLoggingEvent event) {
    boolean notificationEnabled = !DISABLED_VALUE.equals(System.getProperty(FATAL_ERROR_NOTIFICATION_PROPERTY, ENABLED_VALUE));

    return notificationEnabled ||
           !(IdeErrorsDialog.getSubmitter(event.getThrowable()) instanceof ITNReporter) ||
           ApplicationManagerEx.getApplicationEx().isInternal() ||
           event.getThrowable() instanceof OutOfMemoryError;
  }

  /**
   * @noinspection CallToPrintStackTrace
   */
  public void handle(IdeaLoggingEvent event) {
    try {
      if (event.getThrowable() instanceof OutOfMemoryError) {
        processOOMError(event);
      }
      else if (!ourOomOccured) {
        MessagePool.getInstance().addIdeFatalMessage(event);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * @noinspection CallToPrintStackTrace
   */
  private void processOOMError(final IdeaLoggingEvent event) throws InterruptedException, InvocationTargetException {
    ourOomOccured = true;
    event.getThrowable().printStackTrace();

    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        String message = event.getThrowable().getMessage();
        OutOfMemoryDialog.MemoryKind k = message != null && message.indexOf(PARAM_PERMGEN) >= 0
                                         ? OutOfMemoryDialog.MemoryKind.PERM_GEN
                                         : OutOfMemoryDialog.MemoryKind.HEAP;
        new OutOfMemoryDialog(k).show();
      }
    });
  }
}
