/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

/**
 * @author peter
 */
public class CommittableUtil implements ProjectComponent {
  private final Application myApplication;
  private final LinkedHashSet<Committable> myResetQueue = new LinkedHashSet<Committable>();
  private boolean myResetting;

  public CommittableUtil(Application application, CommandProcessor commandProcessor) {
    commandProcessor.addCommandListener(new CommandAdapter() {

      public void commandFinished(CommandEvent event) {
        undoTransparentActionFinished();
      }

      public void undoTransparentActionFinished() {
        if (myResetting) return;
        myResetting = true;
        try {
          for (final Committable committable : myResetQueue) {
            committable.reset();
          }
          myResetQueue.clear();
        }
        finally {
          myResetting = false;
        }
      }
    });
    myApplication = application;
  }

  public void queueReset(Committable committable) {
    if (myResetting && myResetQueue.contains(committable)) return;
    myApplication.assertIsDispatchThread();
    myResetQueue.add(committable);
    if (CommandProcessor.getInstance().getCurrentCommand() == null &&
        !CommandProcessor.getInstance().isUndoTransparentActionInProgress()) {
      boolean b = myResetting;
      myResetting = true;
      try {
        committable.reset();
        myResetQueue.remove(committable);
      }
      finally {
        myResetting = b;
      }
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {

  }

  public void disposeComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }
}
