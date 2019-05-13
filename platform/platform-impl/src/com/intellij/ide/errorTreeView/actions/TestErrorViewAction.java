// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.MessageView;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class TestErrorViewAction extends AnAction{
  private static final int MESSAGE_COUNT = 1000;
  private long myMillis = 0L;
  private int myMessageCount = 0;

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    final ErrorTreeView view = createView(project);
    openView(project, view.getComponent());
    myMillis = 0L;
    myMessageCount = 0;
    new Thread("test error view") {
      @Override
      public void run() {
        for (int idx = 0; idx < MESSAGE_COUNT; idx++) {
          addMessage(view, new String[] {"This is a warning test message" + idx + " line1", "This is a warning test message" + idx + " line2"}, MessageCategory.WARNING);
        }
        while (getMessageCount() < MESSAGE_COUNT) {
          try {
            Thread.sleep(100);
          }
          catch (InterruptedException e1) {
            e1.printStackTrace();
          }
        }
        String statistics = "Duration = " + myMillis;
        addMessage(view, new String[] {statistics}, MessageCategory.STATISTICS);
        System.out.println(statistics);
        while (getMessageCount() < MESSAGE_COUNT + 1) {
          try {
            Thread.sleep(100);
          }
          catch (InterruptedException e1) {
            e1.printStackTrace();
          }
        }
        System.out.println("Expected " + (MESSAGE_COUNT + 1) + " messages;");
        Disposer.dispose(view);
      }
    }.start();
  }

  public synchronized int getMessageCount() {
    return myMessageCount;
  }

  public synchronized void incMessageCount() {
    myMessageCount++;
  }

  private void addMessage(final ErrorTreeView view, final String[] message, final int type) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final long start = System.currentTimeMillis();
      view.addMessage(type, message, null, -1, -1, null);
      final long duration = System.currentTimeMillis() - start;
      myMillis += duration;
      incMessageCount();
    }, ModalityState.NON_MODAL);
  }

  protected abstract ErrorTreeView createView(Project project);
  protected abstract String getContentName();

  protected void openView(Project project, JComponent component) {
    final MessageView messageView = MessageView.SERVICE.getInstance(project);
    final Content content = ContentFactory.SERVICE.getInstance().createContent(component, getContentName(), true);
    messageView.getContentManager().addContent(content);
    messageView.getContentManager().setSelectedContent(content);
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
    if (toolWindow != null) {
      toolWindow.activate(null);
    }
  }

}
