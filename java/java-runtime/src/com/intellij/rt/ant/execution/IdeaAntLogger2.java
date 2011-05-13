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

import com.intellij.rt.execution.junit.segments.PacketWriter;
import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

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

  /**
   * @noinspection HardCodedStringLiteral
   */
  public static final String OUTPUT_PREFIX = "IDEA_ANT_INTEGRATION";

  private final Priority myMessagePriority = new MessagePriority();
  private final Priority myTargetPriority = new StatePriority(Project.MSG_INFO);
  private final Priority myTaskPriority = new StatePriority(Project.MSG_INFO);
  private final Priority myAlwaysSend = new Priority() {
    public void setPriority(int level) {}

    protected boolean shouldSend(int priority) {
      return true;
    }
  };

  public IdeaAntLogger2() {
    guardStreams();
  }

  public synchronized void setMessageOutputLevel(int level) {
    super.setMessageOutputLevel(level);
    myMessagePriority.setPriority(level);
    myTargetPriority.setPriority(level);
    myTaskPriority.setPriority(level);
    myAlwaysSend.setPriority(level);
  }

  public synchronized void buildStarted(BuildEvent event) {
    myAlwaysSend.sendMessage(BUILD, event.getPriority(), "");
  }

  public synchronized void buildFinished(BuildEvent event) {
    myAlwaysSend.sendMessage(BUILD_END, event.getPriority(), event.getException());
  }

  public synchronized void targetStarted(BuildEvent event) {
    myTargetPriority.sendMessage(TARGET, event.getPriority(), event.getTarget().getName());
  }

  public synchronized void targetFinished(BuildEvent event) {
    sendException(event);
    myTargetPriority.sendMessage(TARGET_END, event.getPriority(), event.getException());
  }

  public synchronized void taskStarted(BuildEvent event) {
    myTaskPriority.sendMessage(TASK, event.getPriority(), event.getTask().getTaskName());
  }

  public synchronized void taskFinished(BuildEvent event) {
    sendException(event);
    myTaskPriority.sendMessage(TASK_END, event.getPriority(), event.getException());
  }

  public synchronized void messageLogged(BuildEvent event) {
    if (sendException(event)) return;
    int priority = event.getPriority();
    String message = event.getMessage();
    if (priority == Project.MSG_ERR)
      myMessagePriority.sendMessage(ERROR, priority, message);
    else
      myMessagePriority.sendMessage(MESSAGE, priority, message);
  }

  private boolean sendException(BuildEvent event) {
    Throwable exception = event.getException();
    if (exception != null) {
      myAlwaysSend.sendMessage(EXCEPTION, event.getPriority(), exception);
      return true;
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

  private void send(PacketWriter packet) {
    packet.sendThrough(ourErr);
  }

  private PacketWriter createPacket(char id, int priority) {
    PacketWriter packet = PacketFactory.ourInstance.createPacket(id);
    packet.appendLong(priority);
    return packet;
  }

  private abstract class Priority {
    protected void peformSendMessage(char id, int priority, String text) {
      PacketWriter packet = createPacket(id, priority);
      packet.appendChar(MESSAGE_CONTENT);
      packet.appendLimitedString(text);
      send(packet);
    }

    protected void peformSendMessage(char id, int priority, Throwable throwable) {
      if (throwable != null) {
        PacketWriter packet = createPacket(id, priority);
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));
        packet.appendChar(EXCEPTION_CONTENT);
        packet.appendLimitedString(stackTrace.toString());
        send(packet);
      } else {
        peformSendMessage(id, priority, "");
      }
    }

    public void sendMessage(char id, int priority, String text) {
      if (shouldSend(priority)) peformSendMessage(id, priority, text);
    }

    public void sendMessage(char id, int priority, Throwable throwable) {
      if (shouldSend(priority)) peformSendMessage(id, priority, throwable);
    }

    public abstract void setPriority(int level);
    protected abstract boolean shouldSend(int priority);
  }

  private class MessagePriority extends Priority {
    private int myPriority = Project.MSG_ERR;

    public void setPriority(int level) {
      myPriority = level;
    }

    protected boolean shouldSend(int priority) {
      return priority <= myPriority;
    }
  }

  private class StatePriority extends Priority {
    private boolean myEnabled = true;
    private final int myMinLevel;

    public StatePriority(int minLevel) {
      myMinLevel = minLevel;
    }

    public void setPriority(int level) {
      myEnabled = myMinLevel <= level;
    }

    protected boolean shouldSend(int priority) {
      return myEnabled;
    }
  }
}
