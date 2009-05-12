/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarCustomComponentFactory;
import com.intellij.util.Alarm;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

/**
 * @author peter
 */
class DumbModeIndicator extends StatusBarCustomComponentFactory<DumbModeIndicator.MyLabel> {
  public static final Icon DUMB_MODE_ICON = IconLoader.findIcon("/debugger/killProcess.png");
  private static final Icon EMPTY_ICON = new EmptyIcon(DUMB_MODE_ICON);
  private static final int BLINKING_FREQUENCY = 500;
  private final Set<MyLabel> myComponents = new HashSet<MyLabel>();


  protected DumbModeIndicator() {
    final Alarm alarm = new Alarm();
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      public void beforeEnteringDumbMode() {
      }

      public void enteredDumbMode() {
        for (final MyLabel component : myComponents) {
          component.setIcon(EMPTY_ICON);
        }

        alarm.addRequest(new Runnable() {
          private long lastTime = System.currentTimeMillis();

          public void run() {
            final long curTime = System.currentTimeMillis();
            boolean forceEmptyIcon = curTime - lastTime > 2 * BLINKING_FREQUENCY;
            lastTime = curTime;

            for (final MyLabel component : myComponents) {
              component.setIcon(forceEmptyIcon || component.getIcon() == DUMB_MODE_ICON ? EMPTY_ICON : DUMB_MODE_ICON);
            }
            alarm.addRequest(this, BLINKING_FREQUENCY);
          }
        }, BLINKING_FREQUENCY);
      }

      public void exitDumbMode() {
        alarm.cancelAllRequests();
        for (final MyLabel component : myComponents) {
          component.setIcon(null);
        }
      }
    });
  }

  public MyLabel createComponent(@NotNull StatusBar statusBar) {
    final MyLabel label = new MyLabel();
    myComponents.add(label);
    return label;
  }

  @Override
  public void disposeComponent(@NotNull StatusBar statusBar, @NotNull MyLabel c) {
    myComponents.remove(c);
  }

  static class MyLabel extends JLabel {

    private MyLabel() {
      setToolTipText("Index update is in progress...");
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          Messages.showMessageDialog("<html>" +
                                     "Each time you see this icon in status bar, IntelliJ IDEA is indexing your source<br>" +
                                     "and library files. This is needed for most smart functionality to work properly." +
                                     "<p>" +
                                     "During this process some actions that require these indices won't be available,<br>" +
                                     "although you still can edit your files and work with VCS and file system.<br>" +
                                     "If you need smarter actions like Goto Declaration, Find Usages or refactorings,<br>" +
                                     "please wait until the update is finished. We appreciate your understanding." +
                                     "<p>" +
                                     "If you prefer IntelliJ IDEA always to be smart, you can disable backgroundable<br>" +
                                     "indexing in 'File | Other Settings | Registry | " + DumbServiceImpl.FILE_INDEX_BACKGROUND + "' and restart.<br>" +
                                     "After restart, the indices will be always updated in a modal dialog." +
                                     "</html>", "Background index update", DUMB_MODE_ICON);
        }
      });
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

  }

}
