// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.actions.GotoFrameSourceAction;
import com.intellij.debugger.engine.DebugProcessImpl;
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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Enumeration;
import java.util.NoSuchElementException;

public class ThreadsPanel extends DebuggerTreePanel{
  @NonNls private static final String HELP_ID = "debugging.debugThreads";
  private final Alarm myUpdateLabelsAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private static final int LABELS_UPDATE_DELAY_MS = 200;

  public ThreadsPanel(Project project, final DebuggerStateManager stateManager) {
    super(project, stateManager);

    final Disposable disposable = DebuggerAction.installEditAction(getThreadsTree(), DebuggerActions.EDIT_FRAME_SOURCE);
    registerDisposable(disposable);

    getThreadsTree().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && getThreadsTree().getSelectionCount() == 1) {
          GotoFrameSourceAction.doAction(DataManager.getInstance().getDataContext(getThreadsTree()));
        }
      }
    });
    add(ScrollPaneFactory.createScrollPane(getThreadsTree()), BorderLayout.CENTER);
    stateManager.addListener(new DebuggerContextListener() {
      @Override
      public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
        if (DebuggerSession.Event.ATTACHED == event || DebuggerSession.Event.RESUME == event) {
          startLabelsUpdate();
        }
        else if (DebuggerSession.Event.PAUSE == event || DebuggerSession.Event.DETACHED == event || DebuggerSession.Event.DISPOSE == event) {
          myUpdateLabelsAlarm.cancelAllRequests();
        }
        if (DebuggerSession.Event.DETACHED == event || DebuggerSession.Event.DISPOSE == event) {
          stateManager.removeListener(this);
        }
      }
    });
    startLabelsUpdate();
  }

  private void startLabelsUpdate() {
    if (myUpdateLabelsAlarm.isDisposed()) {
      return;
    }
    myUpdateLabelsAlarm.cancelAllRequests();
    myUpdateLabelsAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        boolean updateScheduled = false;
        try {
          if (isUpdateEnabled()) {
            final ThreadsDebuggerTree tree = getThreadsTree();
            final DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)tree.getModel().getRoot();
            if (root != null) {
              final DebugProcessImpl process = getContext().getDebugProcess();
              if (process != null) {
                process.getManagerThread().invoke(new DebuggerCommandImpl() {
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
        if (session != null && session.isAttached() && !session.isPaused() && !myUpdateLabelsAlarm.isDisposed()) {
          myUpdateLabelsAlarm.addRequest(this, LABELS_UPDATE_DELAY_MS, ModalityState.NON_MODAL);
        }
      }
      
    }, LABELS_UPDATE_DELAY_MS, ModalityState.NON_MODAL);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myUpdateLabelsAlarm);
    super.dispose();
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
    DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction(DebuggerActions.THREADS_PANEL_POPUP);
    return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.THREADS_PANEL_POPUP, group);
  }

  @Override
  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }
  public ThreadsDebuggerTree getThreadsTree() {
    return (ThreadsDebuggerTree) getTree();
  }
}
