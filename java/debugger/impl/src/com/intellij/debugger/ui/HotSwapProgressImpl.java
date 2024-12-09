// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapProgress;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.reference.SoftReference;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.hotswap.HotSwapStatusNotificationManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class HotSwapProgressImpl extends HotSwapProgress {
  static final NotificationGroup NOTIFICATION_GROUP = Registry.is("debugger.hotswap.floating.toolbar")
                                                      ? NotificationGroupManager.getInstance().getNotificationGroup("HotSwap Messages")
                                                      : NotificationGroup.toolWindowGroup("HotSwap", ToolWindowId.DEBUG);

  private final Int2ObjectMap<Map<DebuggerSession, List<String>>> myMessages = new Int2ObjectOpenHashMap<>();
  private final ProgressWindow myProgressWindow;
  private @NlsContexts.ProgressTitle String myTitle = JavaDebuggerBundle.message("progress.hot.swap.title");
  private final MergingUpdateQueue myUpdateQueue;
  private WeakReference<XDebugSession> mySessionRef = null;
  private final List<HotSwapProgressListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public HotSwapProgressImpl(Project project) {
    super(project);
    assert EventQueue.isDispatchThread();
    myProgressWindow = new BackgroundableProcessIndicator(getProject(), myTitle, null, null, true);
    myProgressWindow.setIndeterminate(false);
    myProgressWindow.addStateDelegate(new AbstractProgressIndicatorExBase() {
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

    var debuggerSessions = myMessages.values().stream().flatMap(e -> e.keySet().stream()).collect(Collectors.toUnmodifiableSet());
    boolean addSessionName = debuggerSessions.size() > 1;

    List<String> errors = getMessages(MessageCategory.ERROR, addSessionName);
    List<String> warnings = getMessages(MessageCategory.WARNING, addSessionName);

    if (!errors.isEmpty()) {
      notifyUser(JavaDebuggerBundle.message("status.hot.swap.completed.with.errors"), buildMessage(errors), true, NotificationType.ERROR);
    }
    else if (!warnings.isEmpty()) {
      notifyUser(JavaDebuggerBundle.message("status.hot.swap.completed.with.warnings"), buildMessage(warnings), true,
                 NotificationType.WARNING);
    }
    else if (!myMessages.isEmpty()) {
      List<String> messages = new ArrayList<>();
      for (int key : myMessages.keySet()) {
        messages.addAll(getMessages(key, addSessionName));
      }
      notifyUser("", buildMessage(messages), false, NotificationType.INFORMATION);
    }
  }

  private void notifyUser(@NlsContexts.NotificationTitle String title,
                          @NlsContexts.NotificationContent String message,
                          boolean withRestart,
                          NotificationType type) {
    Notification notification = NOTIFICATION_GROUP.createNotification(title, message, type);
    HotSwapStatusNotificationManager.getInstance(getProject()).trackNotification(notification);
    if (SoftReference.dereference(mySessionRef) != null) {
      if (withRestart) {
        notification.addAction(new RestartHotSwapNotificationAction(mySessionRef));
      }
      notification.addAction(new StopHotSwapNotificationAction(mySessionRef));
    }
    notification.setImportant(false).notify(getProject());
  }

  public void setSessionForActions(@NotNull DebuggerSession session) {
    mySessionRef = new WeakReference<>(session.getXDebugSession());
  }

  private @NotNull @Unmodifiable List<String> getMessages(int category, boolean addSessionName) {
    var sessionMessages = ContainerUtil.notNullize(myMessages.get(category));
    return sessionMessages.entrySet().stream().flatMap(entry -> {
      var stream = entry.getValue().stream();
      return addSessionName ? stream.map(message -> entry.getKey().getSessionName() + ": " + message) : stream;
    }).toList();
  }

  boolean hasErrors() {
    return !getMessages(MessageCategory.ERROR, false).isEmpty();
  }

  private static @NlsSafe String buildMessage(List<String> messages) {
    return StreamEx.of(messages).joining("\n");
  }

  @Override
  public void addMessage(DebuggerSession session, final int type, final String text) {
    Map<DebuggerSession, List<String>> messages = myMessages.get(type);
    if (messages == null) {
      messages = new HashMap<>();
      myMessages.put(type, messages);
    }
    List<String> sessionMessages = messages.get(session);
    if (sessionMessages == null) {
      sessionMessages = new SmartList<>();
      messages.put(session, sessionMessages);
    }
    sessionMessages.add(text);
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

  /**
   * Please do not inline, WeakReference is here for a reason.
   */
  private static class StopHotSwapNotificationAction extends NotificationAction {
    private final WeakReference<XDebugSession> mySessionRef;

    private StopHotSwapNotificationAction(@NotNull WeakReference<XDebugSession> session) {
      super(JavaDebuggerBundle.message("status.hot.swap.completed.stop"));
      mySessionRef = session;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      XDebugSession session = SoftReference.dereference(mySessionRef);
      if (session != null) {
        notification.expire();
        session.stop();
      }
    }
  }

  /**
   * Please do not inline, WeakReference is here for a reason.
   */
  private static class RestartHotSwapNotificationAction extends NotificationAction {
    private final WeakReference<XDebugSession> mySessionRef;

    private RestartHotSwapNotificationAction(@NotNull WeakReference<XDebugSession> session) {
      super(JavaDebuggerBundle.message("status.hot.swap.completed.restart"));
      mySessionRef = session;
    }

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
  }
}
