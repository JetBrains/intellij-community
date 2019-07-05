// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class IdeaFreezeReporter {
  private static final int FREEZE_THRESHOLD = ApplicationManager.getApplication().isInternal() ? 5 : 25; // seconds

  public IdeaFreezeReporter() {
    Application app = ApplicationManager.getApplication();
    if (!app.isEAP() || app.isUnitTestMode() || PluginManagerCore.isRunningFromSources()) {
      return;
    }

    app.getMessageBus().connect().subscribe(IdePerformanceListener.TOPIC, new IdePerformanceListener() {
      final List<ThreadDump> myCurrentDumps = new ArrayList<>();
      List<StackTraceElement> myStacktraceCommonPart = null;
      volatile boolean myFreezeRecording = false;

      @Override
      public void uiFreezeStarted() {
        myFreezeRecording = Registry.is("performance.watcher.freeze.report") && !DebugAttachDetector.isAttached();
      }

      @Override
      public void dumpedThreads(@NotNull File toFile, @NotNull ThreadDump dump) {
        if (myFreezeRecording) {
          myCurrentDumps.add(dump);
          StackTraceElement[] edtStack = dump.getEDTStackTrace();
          if (edtStack != null) {
            if (myStacktraceCommonPart == null) {
              myStacktraceCommonPart = ContainerUtil.newArrayList(edtStack);
            }
            else {
              myStacktraceCommonPart = PerformanceWatcher.getStacktraceCommonPart(myStacktraceCommonPart, edtStack);
            }
          }
        }
      }

      @Override
      public void uiFreezeFinished(int lengthInSeconds) {
        if (!myFreezeRecording) {
          return;
        }
        myFreezeRecording = false;
        if (lengthInSeconds > FREEZE_THRESHOLD &&
            // check that we have at least half of the dumps required
            myCurrentDumps.size() >= Math.max(3, lengthInSeconds * 500 / Registry.intValue("performance.watcher.unresponsive.interval.ms")) &&
            !ContainerUtil.isEmpty(myStacktraceCommonPart)) {
          int size = Math.min(myCurrentDumps.size(), 20); // report up to 20 dumps
          int step = myCurrentDumps.size() / size;
          Attachment[] attachments = new Attachment[size];
          for (int i = 0; i < size; i++) {
            Attachment attachment = new Attachment("dump-" + i + ".txt", myCurrentDumps.get(i*step).getRawDump());
            attachment.setIncluded(true);
            attachments[i] = attachment;
          }
          IdeaLoggingEvent event = createEvent(lengthInSeconds, attachments);
          if (event != null) {
            Throwable t = event.getThrowable();
            if (IdeErrorsDialog.getSubmitter(t, IdeErrorsDialog.findPluginId(t)) instanceof ITNReporter) { // only report to JB
              MessagePool.getInstance().addIdeFatalMessage(event);
            }
          }
        }
        myCurrentDumps.clear();
        myStacktraceCommonPart = null;
      }

      @Nullable
      private IdeaLoggingEvent createEvent(int lengthInSeconds, Attachment[] attachments) {
        boolean allInEdt = edts(myCurrentDumps)
          .map(ThreadInfo::getThreadState)
          .allMatch(Thread.State.RUNNABLE::equals);
        List<StackTraceElement[]> reasonStacks;
        if (allInEdt) {
          reasonStacks = edts(myCurrentDumps).map(ThreadInfo::getStackTrace).toList();
        }
        else {
          reasonStacks = new ArrayList<>();
          long causeThreadId = -1;
          for (ThreadDump dump : myCurrentDumps) {
            if (causeThreadId == -1) {
              // find probable cause thread
              ThreadInfo[] threadInfos = dump.getThreadInfos();
              ThreadInfo edt = ContainerUtil.find(threadInfos, ThreadDumper::isEDT);
              if (edt != null && edt.getThreadState() != Thread.State.RUNNABLE) {
                String lockName = edt.getLockName();
                if (lockName != null && lockName.contains("ReadMostlyRWLock")) {
                  for (ThreadInfo info : threadInfos) {
                    if (info.getThreadState() == Thread.State.RUNNABLE &&
                        ContainerUtil.find(info.getStackTrace(), s ->
                          "runReadAction".equals(s.getMethodName()) || "tryRunReadAction".equals(s.getMethodName())) != null) {
                      causeThreadId = info.getThreadId();
                      reasonStacks.add(info.getStackTrace());
                      break;
                    }
                  }
                }
              }
            }
            else {
              long finalCauseThreadId = causeThreadId;
              ThreadInfo causeThread = ContainerUtil.find(dump.getThreadInfos(), i -> i.getThreadId() == finalCauseThreadId);
              if (causeThread != null) {
                reasonStacks.add(causeThread.getStackTrace());
              }
            }
          }
        }
        if (reasonStacks.isEmpty()) {
          reasonStacks = edts(myCurrentDumps).map(ThreadInfo::getStackTrace).toList(); // fallback EDT threads
        }
        CallTreeNode root = buildTree(reasonStacks);
        List<StackTraceElement> commonStack = findDominantCommonStack(root, reasonStacks.size() / 2);
        if (ContainerUtil.isEmpty(commonStack)) {
          commonStack = myStacktraceCommonPart; // fallback to simple EDT common
        }

        // dump tree
        StringBuilder sb = new StringBuilder();
        LinkedList<CallTreeNode> nodes = new LinkedList<>(root.myChildren);
        while (!nodes.isEmpty()) {
          CallTreeNode node = nodes.removeFirst();
          node.appendIndentedString(sb);
          nodes.addAll(0, ContainerUtil.sorted(node.myChildren, CallTreeNode.TIME_COMPARATOR));
        }
        Attachment attachment = new Attachment("report-" + lengthInSeconds + "s.txt", sb.toString());
        attachment.setIncluded(true);

        if (!ContainerUtil.isEmpty(commonStack)) {
          String edtNote = allInEdt ? "in EDT " : "";
          return LogMessage.createEvent(new Freeze(commonStack),
                                        "Freeze " + edtNote + "for " + lengthInSeconds + " seconds",
                                        ArrayUtil.append(attachments, attachment));
        }
        return null;
      }
    });
  }

  private static StreamEx<ThreadInfo> edts(List<ThreadDump> dumps) {
    return StreamEx.of(dumps)
      .flatArray(ThreadDump::getThreadInfos)
      .filter(ThreadDumper::isEDT);
  }

  @Nullable
  private static List<StackTraceElement> findDominantCommonStack(CallTreeNode root, int threshold) {
    // find dominant
    CallTreeNode node = root.getMostHitChild();
    if (node == null) {
      return null;
    }
    while (!node.myChildren.isEmpty()) {
      CallTreeNode mostHitChild = node.getMostHitChild();
      if (mostHitChild != null && mostHitChild.myTime > threshold) {
        node = mostHitChild;
      }
      else {
        break;
      }
    }
    // build stack
    List<StackTraceElement> res = new ArrayList<>();
    while (node != null && node.myStackTraceElement != null) {
      res.add(node.myStackTraceElement);
      node = node.myParent;
    }
    return res;
  }

  @NotNull
  private static CallTreeNode buildTree(List<StackTraceElement[]> stacks) {
    CallTreeNode root = new CallTreeNode(null, null, 0, 0);
    for (StackTraceElement[] stack : stacks) {
      CallTreeNode node = root;
      for (int i = stack.length - 1; i >= 0; i--) {
        node = node.addCallee(stack[i], 1);
      }
    }
    return root;
  }

  private static class CallTreeNode {
    final StackTraceElement myStackTraceElement;
    final CallTreeNode myParent;
    final List<CallTreeNode> myChildren = ContainerUtil.newSmartList();
    long myTime;
    final int myDepth;

    static final Comparator<CallTreeNode> TIME_COMPARATOR = Comparator.<CallTreeNode>comparingLong(n -> n.myTime).reversed();

    private CallTreeNode(StackTraceElement element, CallTreeNode parent, int depth, long time) {
      myStackTraceElement = element;
      myParent = parent;
      myDepth = depth;
      myTime = time;
    }

    CallTreeNode addCallee(StackTraceElement e, long time) {
      for (CallTreeNode child : myChildren) {
        if (PerformanceWatcher.compareStackTraceElements(child.myStackTraceElement, e)) {
          child.myTime += time;
          return child;
        }
      }
      CallTreeNode child = new CallTreeNode(e, this, myDepth + 1, time);
      myChildren.add(child);
      return child;
    }

    @Nullable
    CallTreeNode getMostHitChild() {
      CallTreeNode currentMax = null;
      for (CallTreeNode child : myChildren) {
        if (currentMax == null || child.myTime > currentMax.myTime) {
          currentMax = child;
        }
      }
      return currentMax;
    }

    @Override
    public String toString() {
      return myTime + " " + myStackTraceElement;
    }

    public void appendIndentedString(StringBuilder builder) {
      StringUtil.repeatSymbol(builder, ' ', myDepth);
      builder.append(myStackTraceElement.getClassName()).append(".").append(myStackTraceElement.getMethodName())
        .append(" ").append(myTime).append("\n");
    }
  }
}
