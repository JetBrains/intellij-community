// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapProgress;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
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
import gnu.trove.TIntObjectHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class HotSwapProgressImpl extends HotSwapProgress {
  static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("HotSwap", ToolWindowId.DEBUG);

  private final TIntObjectHashMap<List<String>> myMessages = new TIntObjectHashMap<>();
  private final ProgressWindow myProgressWindow;
  private String myTitle = DebuggerBundle.message("progress.hot.swap.title");
  private final MergingUpdateQueue myUpdateQueue;
  private WeakReference<XDebugSession> mySessionRef = null;
  private final List<HotSwapProgressListener> myListeners = ContainerUtil.newSmartList();

  public HotSwapProgressImpl(Project project) {
    super(project);
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
      notifyUser(DebuggerBundle.message("status.hot.swap.completed.with.errors"), buildMessage(errors, true), NotificationType.ERROR);
    }
    else if (!warnings.isEmpty()){
      notifyUser(DebuggerBundle.message("status.hot.swap.completed.with.warnings"), buildMessage(warnings, true), NotificationType.WARNING);
    }
    else if (!myMessages.isEmpty()){
      List<String> messages = new ArrayList<>();
      for (int category : myMessages.keys()) {
        messages.addAll(getMessages(category));
      }
      notifyUser("", buildMessage(messages, false), NotificationType.INFORMATION);
    }
  }

  private void notifyUser(String title, String message, NotificationType type) {
    NotificationListener notificationListener = null;
    if (SoftReference.dereference(mySessionRef) != null) {
      notificationListener = new HotSwapNotificationListener(mySessionRef);
    }
    NOTIFICATION_GROUP.createNotification(title, message, type, notificationListener).setImportant(false).notify(getProject());
  }

  private static class HotSwapNotificationListener implements NotificationListener {
    final WeakReference<XDebugSession> mySessionRef;

    public HotSwapNotificationListener(WeakReference<XDebugSession> sessionRef) {
      mySessionRef = sessionRef;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
        return;
      }
      XDebugSession session = SoftReference.dereference(mySessionRef);
      if (session == null) {
        return;
      }
      notification.expire();
      switch (event.getDescription()) {
        case "stop":
          session.stop();
          break;
        case "restart":
          ExecutionEnvironment environment = ((XDebugSessionImpl)session).getExecutionEnvironment();
          if (environment != null) {
            ExecutionUtil.restart(environment);
          }
          break;
      }
    }
  }

  public void setSessionForActions(@NotNull DebuggerSession session) {
    mySessionRef = new WeakReference<>(session.getXDebugSession());
  }

  List<String> getMessages(int category) {
    return ContainerUtil.notNullize(myMessages.get(category));
  }

  private String buildMessage(List<String> messages, boolean withRestart) {
    StringBuilder res = new StringBuilder(StreamEx.of(messages).map(m -> StringUtil.trimEnd(m, ';')).joining("\n"));
    if (SoftReference.dereference(mySessionRef) != null) {
      res.append("\n").append(DebuggerBundle.message("status.hot.swap.completed.stop"));
      if (withRestart) {
        res.append("&nbsp;&nbsp;&nbsp;&nbsp;").append(DebuggerBundle.message("status.hot.swap.completed.restart"));
      }
    }
    return res.toString();
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
  public void setText(final String text) {
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
  public void setTitle(final String text) {
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
    myTitle = DebuggerBundle.message("progress.hot.swap.title") + " : " + session.getSessionName();
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
