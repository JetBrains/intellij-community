/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

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
        StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getIdeFrame(myProject).getStatusBar();
        HyperlinkListener listener = new HyperlinkListener() {
          public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;

            Messages.showMessageDialog("<html>" +
                                       "IntelliJ IDEA is now indexing your source and library files. These indices are<br>" +
                                       "needed for most of the smart functionality to work properly." +
                                       "<p>" +
                                       "During this process some actions that require these indices won't be available,<br>" +
                                       "although you still can edit your files and work with VCS and file system.<br>" +
                                       "If you need smarter actions like Goto Declaration, Find Usages or refactorings,<br>" +
                                       "please wait until the update is finished. We appreciate your understanding." +
                                       "</html>", "Don't panic!", null);
          }
        };
        myHandler = statusBar.notifyProgressByBalloon(MessageType.WARNING,
                                                      "Index update is in progress...<br>" +
                                                      "During this process some actions that require these indices won't be available.<br>" +
                                                      "<a href=\'help\'>Click here for more info.</a>",
                                                      null,
                                                      listener);
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
