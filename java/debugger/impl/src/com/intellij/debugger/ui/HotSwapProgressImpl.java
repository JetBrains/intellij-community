// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapProgress;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.reference.SoftReference;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class HotSwapProgressImpl extends HotSwapProgress {
  static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("HotSwap", ToolWindowId.DEBUG);

  private final Int2ObjectMap<List<String>> myMessages = new Int2ObjectOpenHashMap<>();
  private final ProgressWindow myProgressWindow;
  private @NlsContexts.ProgressTitle String myTitle = JavaDebuggerBundle.message("progress.hot.swap.title");
  private final MergingUpdateQueue myUpdateQueue;
  private WeakReference<XDebugSession> mySessionRef = null;
  private final List<HotSwapProgressListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public HotSwapProgressImpl(Project project) {
    super(project);
    assert EventQueue.isDispatchThread();
    myProgressWindow = new BackgroundableProcessIndicator(getProject(), myTitle, new PerformInBackgroundOption() {
      @Override
      public boolean shouldStartInBackground() {
        return DebuggerSettings.getInstance().HOTSWAP_IN_BACKGROUND;
      }
    }, null, null, true);
    myProgressWindow.setIndeterminate(false);
    myProgressWindow.addStateDelegate(new AbstractProgressIndicatorExBase(){
      @Override
      public void cancel() {
        super.cancel();
        HotSwapProgressImpl.this.cancel();
      }
    });
    myUpdateQueue = new MergingUpdateQueue("HotSwapProgress update queue", 100, true, null, myProgressWindow);
  }

  @Override
  public void cancel() {
    super.cancel();
    for (HotSwapProgressListener listener : myListeners) {
      listener.onCancel();
    }
  }

  @Override
  public void finished() {
    super.finished();

    for (HotSwapProgressListener listener : myListeners) {
      listener.onFinish();
    }

    List<String> errors = getMessages(MessageCategory.ERROR);
    List<String> warnings = getMessages(MessageCategory.WARNING);

    if (!errors.isEmpty()) {
      notifyUser(JavaDebuggerBundle.message("status.hot.swap.completed.with.errors"), buildMessage(errors), true, NotificationType.ERROR);
    }
    else if (!warnings.isEmpty()){
      notifyUser(JavaDebuggerBundle.message("status.hot.swap.completed.with.warnings"), buildMessage(warnings), true,
                 NotificationType.WARNING);
    }
    else if (!myMessages.isEmpty()){
      List<String> messages = new ArrayList<>();
      for (IntIterator iterator = myMessages.keySet().iterator(); iterator.hasNext(); ) {
        messages.addAll(getMessages(iterator.nextInt()));
      }
      notifyUser("", buildMessage(messages), false, NotificationType.INFORMATION);
    }
  }

  private void notifyUser(@NlsContexts.NotificationTitle String title,
                          @NlsContexts.NotificationContent String message,
                          boolean withRestart,
                          NotificationType type) {
    Notification notification = NOTIFICATION_GROUP.createNotification(title, message, type);
    if (SoftReference.dereference(mySessionRef) != null) {
      notification.addAction(new NotificationAction(JavaDebuggerBundle.message("status.hot.swap.completed.stop")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          XDebugSession session = SoftReference.dereference(mySessionRef);
          if (session != null) {
            notification.expire();
            session.stop();
          }
        }
      });
      if (withRestart) {
        notification.addAction(new NotificationAction(JavaDebuggerBundle.message("status.hot.swap.completed.restart")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
            XDebugSession session = SoftReference.dereference(mySessionRef);
            if (session != null) {
              notification.expire();
              ExecutionEnvironment environment = ((XDebugSessionImpl)session).getExecutionEnvironment();
              if (environment != null) {
                ExecutionUtil.restart(environment);
              }
            }
          }
        });
      }
    }
    notification.setImportant(false).notify(getProject());
  }

  public void setSessionForActions(@NotNull DebuggerSession session) {
    mySessionRef = new WeakReference<>(session.getXDebugSession());
  }

  List<String> getMessages(int category) {
    return ContainerUtil.notNullize(myMessages.get(category));
  }

  private static @NlsSafe String buildMessage(List<String> messages) {
    return StreamEx.of(messages).map(m -> StringUtil.trimEnd(m, ';')).joining("\n");
  }

  @Override
  public void addMessage(DebuggerSession session, final int type, final String text) {
    List<String> messages = myMessages.get(type);
    if (messages == null) {
      messages = new SmartList<>();
      myMessages.put(type, messages);
    }
    messages.add(session.getSessionName() + ": " + text + ";");
  }

  @Override
  public void setText(final @NlsContexts.ProgressText String text) {
    myUpdateQueue.queue(new Update("Text") {
      @Override
      public void run() {
        DebuggerInvocationUtil.invokeLater(getProject(), () -> {
          if (!myProgressWindow.isCanceled() && myProgressWindow.isRunning()) {
            myProgressWindow.setText(text);
          }
        }, myProgressWindow.getModalityState());
      }
    });
  }

  @Override
  public void setTitle(final @NlsContexts.ProgressTitle @NotNull String text) {
    DebuggerInvocationUtil.invokeLater(getProject(), () -> {
      if (!myProgressWindow.isCanceled() && myProgressWindow.isRunning()) {
      myProgressWindow.setTitle(text);
      }
    }, myProgressWindow.getModalityState());

  }

  @Override
  public void setFraction(final double v) {
    DebuggerInvocationUtil.invokeLater(getProject(), () -> {
      if (!myProgressWindow.isCanceled() && myProgressWindow.isRunning()) {
      myProgressWindow.setFraction(v);
      }
    }, myProgressWindow.getModalityState());
  }

  @Override
  public boolean isCancelled() {
    return myProgressWindow.isCanceled();
  }

  public ProgressIndicator getProgressIndicator() {
     return myProgressWindow;
  }

  @Override
  public void setDebuggerSession(DebuggerSession session) {
    myTitle = JavaDebuggerBundle.message("progress.hot.swap.title") + " : " + session.getSessionName();
    myProgressWindow.setTitle(myTitle);
  }

  void addProgressListener(@NotNull HotSwapProgressListener listener) {
    myListeners.add(listener);
  }

  interface HotSwapProgressListener {
    default void onCancel() {
    }

    default void onFinish() {
    }
  }
}
