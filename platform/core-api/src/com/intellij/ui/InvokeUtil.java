package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

public class InvokeUtil {
  private InvokeUtil() {
  }

  public static void invokeAndWait(@NotNull Runnable runnable) throws InvocationTargetException, InterruptedException {
    Application application = ApplicationManager.getApplication();
    assert !application.isDispatchThread() : "Must not be invoked from AWT dispatch thread";
    if (application.isReadAccessAllowed()) {
      // make ApplicationImpl catch deadlock situation with readLock held
      application.invokeAndWait(runnable, application.getDefaultModalityState());
      return;
    }
    SwingUtilities.invokeAndWait(runnable);
  }

  public static void runOrInvokeAndWait(@NotNull Runnable runnable) throws InvocationTargetException, InterruptedException {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      runnable.run();
    }
    else {
      invokeAndWait(runnable);
    }
  }
}
