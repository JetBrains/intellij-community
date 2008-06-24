/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
