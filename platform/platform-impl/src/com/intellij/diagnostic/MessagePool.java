/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic;

import com.intellij.concurrency.JobScheduler;
import com.intellij.notification.*;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import org.apache.log4j.Category;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MessagePool {
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("IDE Fatal Errors");


  private static final int MAX_POOL_SIZE_FOR_FATALS = 100;

  private final List<AbstractMessage> myIdeFatals = new ArrayList<AbstractMessage>();

  private final Set<MessagePoolListener> myListeners = new HashSet<MessagePoolListener>();

  private final MessageGrouper myFatalsGrouper;
  private boolean ourJvmIsShuttingDown = false;

  MessagePool(int maxGroupSize, int timeout) {
    myFatalsGrouper = new MessageGrouper(timeout, maxGroupSize);
    JobScheduler.getScheduler().scheduleAtFixedRate(myFatalsGrouper, (long)300, (long)300, TimeUnit.MILLISECONDS);
  }

  private static class MessagePoolHolder {
    private static final MessagePool ourInstance = new MessagePool(20, 1000);
  }

  public static MessagePool getInstance() {
    return MessagePoolHolder.ourInstance;
  }

  public void addIdeFatalMessage(final IdeaLoggingEvent aEvent) {
    Object data = aEvent.getData();
    final LogMessage message = data instanceof LogMessage ? (LogMessage)data : new LogMessage(aEvent);
    if (myIdeFatals.size() < MAX_POOL_SIZE_FOR_FATALS) {
      if (myFatalsGrouper.addToGroup(message)) {
        String title = "<a href='xxx'>" + getTitle(message) + "</a>";
        String notificationText = getNotificationText(message);
        Notification notification =
          new Notification(NOTIFICATION_GROUP.getDisplayId(), title, notificationText, NotificationType.ERROR, new NotificationListener() {
            @Override
            public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
              Object source = event.getSource();
              if (source instanceof Component) {
                Window window = SwingUtilities.getWindowAncestor((Component)source);
                if (window instanceof IdeFrame) {
                  StatusBarWidget widget = ((IdeStatusBarImpl)((IdeFrame)window).getStatusBar()).getWidget(IdeMessagePanel.FATAL_ERROR);
                  if (widget instanceof IdeMessagePanel) {
                    ((IdeMessagePanel)widget).openFatals(message);
                  }
                }
              }
            }
          }) {
            @Override
            public void expire() {
              super.expire();
              if (!message.isRead()) {
                message.setRead(true);
              }
              clearFatals();
              notifyListenersRead();
            }
          };
        notification.notify(null);
        message.setNotification(notification);
      }
    } else if (myIdeFatals.size() == MAX_POOL_SIZE_FOR_FATALS) {
      String msg = DiagnosticBundle.message("error.monitor.too.many.errors");
      myFatalsGrouper.addToGroup(new LogMessage(new LoggingEvent(msg, Category.getRoot(), Priority.ERROR, null, new TooManyErrorsException())));
      NOTIFICATION_GROUP.createNotification(msg, NotificationType.ERROR).notify(null);
    }
  }

  private static String getNotificationText(LogMessage message) {
    String text = message.getMessage();
    if (message instanceof LogMessageEx) {
      String result = ((LogMessageEx)message).getNotificationText();
      if (result != null) {
        text = StringUtil.stripHtml(result, false);
      }
    }
    return text;
  }

  private static String getTitle(LogMessage message) {
    if (message instanceof LogMessageEx) {
      return ((LogMessageEx)message).getTitle();
    }
    Throwable throwable = message.getThrowable();
    return throwable == null ? "IDE Fatal Error" : throwable.getClass().getSimpleName();
  }

  public boolean hasUnreadMessages() {
    for (AbstractMessage message : myIdeFatals) {
      if (!message.isRead()) return true;
    }
    return false;
  }

  public List<AbstractMessage> getFatalErrors(boolean aIncludeReadMessages, boolean aIncludeSubmittedMessages) {
    List<AbstractMessage> result = new ArrayList<AbstractMessage>();
    for (AbstractMessage each : myIdeFatals) {
      if (!each.isRead() && !each.isSubmitted()) {
        result.add(each);
      }
      else if ((each.isRead() && aIncludeReadMessages) || (each.isSubmitted() && aIncludeSubmittedMessages)) {
        result.add(each);
      }
    }
    return result;
  }

  public void clearFatals() {
    for (AbstractMessage fatal : myIdeFatals) {
      fatal.setRead(true); // expire notifications
    }

    myIdeFatals.clear();
    notifyListenersClear();
  }

  public void addListener(MessagePoolListener aListener) {
    myListeners.add(aListener);
  }

  public void removeListener(MessagePoolListener aListener) {
    myListeners.remove(aListener);
  }

  private void notifyListenersAdd() {
    if (ourJvmIsShuttingDown) return;

    final MessagePoolListener[] messagePoolListeners = myListeners.toArray(new MessagePoolListener[myListeners.size()]);
    for (MessagePoolListener messagePoolListener : messagePoolListeners) {
      messagePoolListener.newEntryAdded();
    }
  }

  private void notifyListenersClear() {
    final MessagePoolListener[] messagePoolListeners = myListeners.toArray(new MessagePoolListener[myListeners.size()]);
    for (MessagePoolListener messagePoolListener : messagePoolListeners) {
      messagePoolListener.poolCleared();
    }
  }

  private void notifyListenersRead() {
    final MessagePoolListener[] messagePoolListeners = myListeners.toArray(new MessagePoolListener[myListeners.size()]);
    for (MessagePoolListener messagePoolListener : messagePoolListeners) {
      messagePoolListener.entryWasRead();
    }
  }

  public void setJvmIsShuttingDown() {
    ourJvmIsShuttingDown = true;
  }

  private class MessageGrouper implements Runnable {

    private final int myTimeOut;
    private final int myMaxGroupSize;

    private final List<AbstractMessage> myMessages = new ArrayList<AbstractMessage>();
    private int myAccumulatedTime;

    public MessageGrouper(int timeout, int maxGroupSize) {
      myTimeOut = timeout;
      myMaxGroupSize = maxGroupSize;
    }

    public void run() {
      myAccumulatedTime += 300;
      if (myAccumulatedTime > myTimeOut) {
        synchronized(myMessages) {
          if (myMessages.size() > 0) {
            post();
          }
        }
      }
    }

    private void post() {
      AbstractMessage message;
      if (myMessages.size() == 1) {
        message = myMessages.get(0);
      } else {
        message = new GroupedLogMessage(new ArrayList<AbstractMessage>(myMessages));
      }
      myMessages.clear();
      myIdeFatals.add(message);
      notifyListenersAdd();
      myAccumulatedTime = 0;
    }

    public boolean addToGroup(@NotNull AbstractMessage message) {
      myAccumulatedTime = 0;
      boolean result = myMessages.isEmpty();
      synchronized(myMessages) {
        myMessages.add(message);
        if (myMessages.size() >= myMaxGroupSize) {
          post();
        }
      }
      return result;
    }
  }

  public static class TooManyErrorsException extends Exception {
    TooManyErrorsException() {
      super(DiagnosticBundle.message("error.monitor.too.many.errors"));
    }
  }
}
