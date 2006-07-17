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
  private static final Application ourApplication = ApplicationManager.getApplication();
  private static final LinkedHashSet<Committable> ourResetQueue = new LinkedHashSet<Committable>();

  {
    CommandProcessor.getInstance().addCommandListener(new CommandAdapter() {

      public void commandFinished(CommandEvent event) {
        undoTransparentActionFinished();
      }

      public void undoTransparentActionFinished() {
        for (final Committable committable : ourResetQueue) {
          committable.reset();
        }
        ourResetQueue.clear();
      }
    });
  }

  public static void queueReset(Committable committable) {
    ourApplication.assertIsDispatchThread();
    if (CommandProcessor.getInstance().getCurrentCommand() != null || CommandProcessor.getInstance().isUndoTransparentActionInProgress()) {
      ourResetQueue.add(committable);
    } else {
      committable.reset();
    }
  }

}
