/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.ui.popup.BalloonHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DumbModeIndicator implements ProjectComponent {
  private final Project myProject;

  public DumbModeIndicator(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    myProject.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      BalloonHandler myHandler;

      public void beforeEnteringDumbMode() {
      }

      public void enteredDumbMode() {
        myHandler = DumbService.getInstance(myProject).showDumbModeNotification(
          "Index update is in progress...<br>" +
          "During this process some actions that require these indices won't be available.<br>" +
          "<a href=\'help\'>Click here for more info</a>");
      }

      public void exitDumbMode() {
        if (myHandler != null) myHandler.hide();
        myHandler = null;
      }
    });
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return DumbModeIndicator.class.getSimpleName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
