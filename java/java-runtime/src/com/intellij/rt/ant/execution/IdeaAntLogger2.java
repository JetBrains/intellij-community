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
package com.intellij.rt.ant.execution;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;

public final class IdeaAntLogger2 extends DefaultLogger {
  static SegmentedOutputStream ourErr;
  public static final char MESSAGE_CONTENT = 'M';
  public static final char EXCEPTION_CONTENT = 'X';
  public static final char INPUT_REQUEST = 'I';
  public static final char BUILD_END = 'b';
  public static final char BUILD = 'B';
  public static final char TARGET = 'G';
  public static final char TARGET_END = 'g';
  public static final char TASK = 'T';
  public static final char TASK_END = 't';
  public static final char MESSAGE = 'M';
  public static final char ERROR = 'E';
  public static final char EXCEPTION = 'X';
  public static final char EXCEPTION_LINE_SEPARATOR = 0;

  public static final String OUTPUT_PREFIX = "IDEA_ANT_INTEGRATION";

  private final ThreadLocal<Deque<String>> myCallingTasks = new ThreadLocal<Deque<String>>() {
    @Override
    protected Deque<String> initialValue() {
      return new ArrayDeque<>();
    }
  };

  private final Priority myMessagePriority = new MessagePriority();
  private final Priority myTargetPriority = new StatePriority(Project.MSG_INFO);
  private final Priority myTaskPriority = new StatePriority(Project.MSG_VERBOSE);
  private final Priority myAlwaysSend = new Priority() {
    @Override
    public void setPriority(int level) {}

    @Override
    protected boolean shouldSend(int priority) {
      return true;
    }
  };

  public IdeaAntLogger2() {
    guardStreams();
  }

  @Override
  public synchronized void setMessageOutputLevel(int level) {
    super.setMessageOutputLevel(level);
    myMessagePriority.setPriority(level);
    myTargetPriority.setPriority(level);
    myTaskPriority.setPriority(level);
    myAlwaysSend.setPriority(level);
  }

  @Override
  public synchronized void buildStarted(BuildEvent event) {
    myAlwaysSend.sendMessage(BUILD, event.getPriority(), "");
  }

  @Override
  public synchronized void buildFinished(BuildEvent event) {
    myAlwaysSend.sendMessage(BUILD_END, event.getPriority(), event.getException());
  }

  @Override
  public synchronized void targetStarted(BuildEvent event) {
    myTargetPriority.sendMessage(TARGET, event.getPriority(), event.getTarget().getName());
  }

  @Override
  public synchronized void targetFinished(BuildEvent event) {
    sendException(event, true);
    myTargetPriority.sendMessage(TARGET_END, event.getPriority(), event.getException());
  }

  @Override
  public synchronized void taskStarted(BuildEvent event) {
    final Task task = event.getTask();
    String taskName = task.getTaskName();
    if (taskName == null) {
      taskName = task.getClass().getSimpleName();
    }
    getTaskCallStack().push(taskName);
    myTaskPriority.sendMessage(TASK, event.getPriority(), taskName);
  }

  @Override
  public synchronized void taskFinished(BuildEvent event) {
    try {
      sendException(event, true);
      myTaskPriority.sendMessage(TASK_END, event.getPriority(), event.getException());
    }
    finally {
      getTaskCallStack().pop();
    }
  }

  @Override
  public synchronized void messageLogged(BuildEvent event) {
    final boolean failOnError = isFailOnError(event);
    if (sendException(event, failOnError)) {
      return;
    }

    int priority = event.getPriority();
    if (priority == Project.MSG_ERR && !failOnError) {
      // some ant tasks (like Copy) with 'failOnError' attribute set to 'false'
      // send warnings with priority level = Project.MSG_ERR
      // this heuristic corrects the priority level, so that IDEA considers the message not as an error but as a warning
      priority = Project.MSG_WARN;
    }

    final String message = event.getMessage();

    if (priority == Project.MSG_ERR) {
      myMessagePriority.sendMessage(ERROR, priority, message);
    }
    else {
      myMessagePriority.sendMessage(MESSAGE, priority, message);
    }
  }

  private static boolean isFailOnError(BuildEvent event) {
    final Task task = event.getTask();
    if (task != null) {
      try {
        final Field field = task.getClass().getDeclaredField("failonerror");
        field.setAccessible(true);
        return !Boolean.FALSE.equals(field.get(task));
      }
      catch (Exception ignored) {
      }
    }
    return true; // default value
  }

  private boolean sendException(BuildEvent event, boolean isFailOnError) {
    Throwable exception = event.getException();
    if (exception != null) {
      final boolean insideTryTask = getTaskCallStack().contains("try");
      if (isFailOnError && !insideTryTask) {
        myAlwaysSend.sendMessage(EXCEPTION, event.getPriority(), exception);
        return true;
      }
      myMessagePriority.sendMessage(MESSAGE, insideTryTask? Project.MSG_VERBOSE : Project.MSG_WARN, exception.getMessage());
    }
    return false;
  }

  public static void guardStreams() {
    if (ourErr != null) {
      return;
    }
    PrintStream err = System.err;
    ourErr = new SegmentedOutputStream(err);
    System.setErr(new PrintStream(ourErr));
    ourErr.sendStart();
  }

  private static void send(PacketWriter packet) {
    packet.sendThrough(ourErr);
  }

  private static PacketWriter createPacket(char id, int priority) {
    PacketWriter packet = PacketFactory.ourInstance.createPacket(id);
    packet.appendLong(priority);
    return packet;
  }

  private Deque<String> getTaskCallStack() {
    return myCallingTasks.get();
  }

  private abstract static class Priority {
    protected void performSendMessage(char id, int priority, String text) {
      PacketWriter packet = createPacket(id, priority);
      packet.appendChar(MESSAGE_CONTENT);
      packet.appendLimitedString(text);
      send(packet);
    }

    protected void performSendMessage(char id, int priority, Throwable throwable) {
      if (throwable != null) {
        PacketWriter packet = createPacket(id, priority);
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));
        packet.appendChar(EXCEPTION_CONTENT);
        packet.appendLimitedString(stackTrace.toString());
        send(packet);
      } else {
        performSendMessage(id, priority, "");
      }
    }

    public void sendMessage(char id, int priority, String text) {
      if (shouldSend(priority)) performSendMessage(id, priority, text);
    }

    public void sendMessage(char id, int priority, Throwable throwable) {
      if (shouldSend(priority)) performSendMessage(id, priority, throwable);
    }

    public abstract void setPriority(int level);
    protected abstract boolean shouldSend(int priority);
  }

  private static class MessagePriority extends Priority {
    private int myPriority = Project.MSG_ERR;

    @Override
    public void setPriority(int level) {
      myPriority = level;
    }

    @Override
    protected boolean shouldSend(int priority) {
      return priority <= myPriority;
    }
  }

  private static class StatePriority extends Priority {
    private boolean myEnabled = true;
    private final int myMinLevel;

    StatePriority(int minLevel) {
      myMinLevel = minLevel;
    }

    @Override
    public void setPriority(int level) {
      myEnabled = myMinLevel <= level;
    }

    @Override
    protected boolean shouldSend(int priority) {
      return myEnabled;
    }
  }
}
