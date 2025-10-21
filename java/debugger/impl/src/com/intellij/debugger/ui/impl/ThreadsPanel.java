// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.GotoFrameSourceAction;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.SingleEdtTaskScheduler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Enumeration;
import java.util.NoSuchElementException;

public final class ThreadsPanel extends DebuggerTreePanel {
  private static final @NonNls String POPUP_ACTION_NAME = "Debugger.ThreadsPanelPopup";
  private static final @NonNls String HELP_ID = "debugging.debugThreads";
  private final SingleEdtTaskScheduler updateLabelsAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();
  private static final int LABELS_UPDATE_DELAY_MS = 200;

  public ThreadsPanel(Project project, final DebuggerStateManager stateManager) {
    super(project, stateManager);

    final Disposable disposable = DebuggerAction.installEditAction(getThreadsTree(), "Debugger.EditFrameSource");
    registerDisposable(disposable);

    getThreadsTree().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && getThreadsTree().getSelectionCount() == 1) {
          GotoFrameSourceAction.doAction(DataManager.getInstance().getDataContext(getThreadsTree()));
        }
      }
    });
    add(ScrollPaneFactory.createScrollPane(getThreadsTree(), true), BorderLayout.CENTER);
    stateManager.addListener(new DebuggerContextListener() {
      @Override
      public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
        if (DebuggerSession.Event.ATTACHED == event || DebuggerSession.Event.RESUME == event) {
          startLabelsUpdate();
        }
        else if (DebuggerSession.Event.PAUSE == event || DebuggerSession.Event.DETACHED == event || DebuggerSession.Event.DISPOSE == event) {
          updateLabelsAlarm.cancel();
        }
        if (DebuggerSession.Event.DETACHED == event || DebuggerSession.Event.DISPOSE == event) {
          stateManager.removeListener(this);
        }
      }
    });
    startLabelsUpdate();
  }

  private void startLabelsUpdate() {
    if (updateLabelsAlarm.isDisposed()) {
      return;
    }

    updateLabelsAlarm.cancelAndRequest(LABELS_UPDATE_DELAY_MS, new Runnable() {
      @Override
      public void run() {
        boolean updateScheduled = false;
        try {
          if (isUpdateEnabled()) {
            final ThreadsDebuggerTree tree = getThreadsTree();
            final DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)tree.getModel().getRoot();
            if (root != null) {
              DebuggerManagerThreadImpl managerThread = getContext().getManagerThread();
              if (managerThread != null) {
                managerThread.schedule(new DebuggerCommandImpl() {
                  @Override
                  protected void action() {
                    try {
                      updateNodeLabels(root);
                    }
                    finally {
                      reschedule();
                    }
                  }

                  @Override
                  protected void commandCancelled() {
                    reschedule();
                  }
                });
                updateScheduled = true;
              }
            }
          }
        }
        finally {
          if (!updateScheduled) {
            reschedule();
          }
        }
      }

      private void reschedule() {
        final DebuggerSession session = getContext().getDebuggerSession();
        if (session != null && session.isAttached() && !session.isPaused() && !updateLabelsAlarm.isDisposed()) {
          ApplicationManager.getApplication().invokeLater(() -> updateLabelsAlarm.request(LABELS_UPDATE_DELAY_MS, this), ModalityState.any());
        }
      }
    });
  }

  @Override
  public void dispose() {
    updateLabelsAlarm.dispose();
    super.dispose();
  }

  public JComponent getDefaultFocusedComponent() {
    return getThreadsTree();
  }

  private static void updateNodeLabels(DebuggerTreeNodeImpl from) {
    Enumeration children = from.children();
    try {
      while (children.hasMoreElements()) {
        DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)children.nextElement();
        child.getDescriptor().updateRepresentation(null, child::labelChanged);
        updateNodeLabels(child);
      }
    }
    catch (NoSuchElementException ignored) { // children have changed - just skip
    }
  }

  @Override
  protected DebuggerTree createTreeView() {
    return new ThreadsDebuggerTree(getProject());
  }

  @Override
  protected ActionPopupMenu createPopupMenu() {
    DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction(POPUP_ACTION_NAME);
    return ActionManager.getInstance().createActionPopupMenu(POPUP_ACTION_NAME, group);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(PlatformCoreDataKeys.HELP_ID, HELP_ID);
  }

  public ThreadsDebuggerTree getThreadsTree() {
    return (ThreadsDebuggerTree)getTree();
  }
}