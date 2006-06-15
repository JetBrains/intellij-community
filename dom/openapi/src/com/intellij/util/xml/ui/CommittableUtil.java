/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;

import java.util.LinkedHashSet;

/**
 * @author peter
 */
public class CommittableUtil {
  private static int myCount;
  private static final Application ourApplication = ApplicationManager.getApplication();
  private static final LinkedHashSet<Runnable> ourRunnables = new LinkedHashSet<Runnable>();

  static {
    CommandProcessor.getInstance().addCommandListener(new CommandAdapter() {

      public void commandFinished(CommandEvent event) {
        undoTransparentActionFinished();
      }

      public void undoTransparentActionFinished() {
        if (!ourRunnables.isEmpty()) {
          final Runnable[] runnables = ourRunnables.toArray(new Runnable[ourRunnables.size()]);
          ourRunnables.clear();
          for (final Runnable runnable : runnables) {
            runnable.run();
          }
        }
      }

    });
  }

  public static void runAfterCommandFinish(Runnable runnable) {
    if (CommandProcessor.getInstance().getCurrentCommand() == null) {
      runnable.run();
    }
    ourRunnables.add(runnable);
  }

  public static void commit(Runnable runnable) {
    ourApplication.assertIsDispatchThread();
    myCount++;
    try {
      runnable.run();
    } finally {
      myCount--;
    }
  }

  public static boolean isCommitting() {
    ourApplication.assertIsDispatchThread();
    return myCount > 0;
  }

}
