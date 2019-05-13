// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
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

      @Override
      public void dumpedThreads(@NotNull File toFile, @NotNull ThreadDump dump) {
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

      @Override
      public void uiFreezeFinished(int lengthInSeconds) {
        if (Registry.is("performance.watcher.freeze.report") &&
            lengthInSeconds > FREEZE_THRESHOLD &&
            // check that we have at least half of the dumps required
            myCurrentDumps.size() >= Math.max(3, lengthInSeconds * 500 / Registry.intValue("performance.watcher.unresponsive.interval.ms")) &&
            !ContainerUtil.isEmpty(myStacktraceCommonPart) &&
            !DebugAttachDetector.isAttached()) {
          int size = Math.min(myCurrentDumps.size(), 20); // report up to 20 dumps
          Attachment[] attachments = new Attachment[size];
          for (int i = 0; i < size; i++) {
            Attachment attachment = new Attachment("dump-" + i + ".txt", myCurrentDumps.get(i).getRawDump());
            attachment.setIncluded(true);
            attachments[i] = attachment;
          }
          IdeaLoggingEvent event = createEvent(lengthInSeconds, attachments);
          if (event != null) {
            MessagePool.getInstance().addIdeFatalMessage(event);
          }
        }
        myCurrentDumps.clear();
        myStacktraceCommonPart = null;
      }

      @Nullable
      private IdeaLoggingEvent createEvent(int lengthInSeconds, Attachment[] attachments) {
        boolean allInEdt = StreamEx.of(myCurrentDumps)
          .flatArray(ThreadDump::getThreadInfos)
          .filter(ThreadDumper::isEDT)
          .map(ThreadInfo::getThreadState)
          .allMatch(Thread.State.RUNNABLE::equals);
        List<StackTraceElement[]> reasonStacks;
        if (allInEdt) {
          reasonStacks = StreamEx.of(myCurrentDumps)
            .flatArray(ThreadDump::getThreadInfos)
            .filter(ThreadDumper::isEDT)
            .map(ThreadInfo::getStackTrace)
            .toList();
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
                        ContainerUtil.find(info.getStackTrace(), s -> "runReadAction".equals(s.getMethodName())) != null) {
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
        List<StackTraceElement> commonStack = findDominantCommonStack(reasonStacks);
        if (ContainerUtil.isEmpty(commonStack)) {
          commonStack = myStacktraceCommonPart; // fallback to simple EDT common
        }
        if (!ContainerUtil.isEmpty(commonStack)) {
          String edtNote = allInEdt ? "in EDT " : "";
          return LogMessage.createEvent(new Freeze(commonStack),
                                        "Freeze " + edtNote + "for " + lengthInSeconds + " seconds",
                                        attachments);
        }
        return null;
      }
    });
  }

  @Nullable
  private static List<StackTraceElement> findDominantCommonStack(List<StackTraceElement[]> stacks) {
    CallTreeNode root = new CallTreeNode(null, null);
    // build tree
    for (StackTraceElement[] stack : stacks) {
      CallTreeNode node = root;
      for (int i = stack.length - 1; i >= 0; i--) {
        node = node.addCallee(stack[i]);
      }
    }
    // find dominant
    int half = stacks.size() / 2;
    CallTreeNode node = root.getMostHitChild();
    if (node == null) {
      return null;
    }
    while (!node.myChildren.isEmpty()) {
      CallTreeNode mostHitChild = node.getMostHitChild();
      if (mostHitChild != null && mostHitChild.myTotalHits > half) {
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

  private static class CallTreeNode {
    final StackTraceElement myStackTraceElement;
    final CallTreeNode myParent;
    final List<CallTreeNode> myChildren = ContainerUtil.newSmartList();
    int myTotalHits = 1;

    private CallTreeNode(StackTraceElement element, CallTreeNode parent) {
      myStackTraceElement = element;
      myParent = parent;
    }

    CallTreeNode addCallee(StackTraceElement e) {
      for (CallTreeNode child : myChildren) {
        if (PerformanceWatcher.compareStackTraceElements(child.myStackTraceElement, e)) {
          child.myTotalHits++;
          return child;
        }
      }
      CallTreeNode child = new CallTreeNode(e, this);
      myChildren.add(child);
      return child;
    }

    @Nullable
    CallTreeNode getMostHitChild() {
      CallTreeNode currentMax = null;
      for (CallTreeNode child : myChildren) {
        if (currentMax == null || child.myTotalHits > currentMax.myTotalHits) {
          currentMax = child;
        }
      }
      return currentMax;
    }

    @Override
    public String toString() {
      return myTotalHits + " " + myStackTraceElement;
    }
  }
}
