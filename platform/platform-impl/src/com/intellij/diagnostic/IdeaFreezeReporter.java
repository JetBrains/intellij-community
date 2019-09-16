// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.management.ThreadInfo;
import java.util.*;

final class IdeaFreezeReporter implements IdePerformanceListener {
  private static final int FREEZE_THRESHOLD = ApplicationManager.getApplication().isInternal() ? 5 : 25; // seconds
  private static final String REPORT_PREFIX = "report";
  private static final String DUMP_PREFIX = "dump";

  private volatile SamplingTask myDumpTask;
  final List<ThreadDump> myCurrentDumps = new ArrayList<>();
  List<StackTraceElement> myStacktraceCommonPart = null;

  IdeaFreezeReporter() {
    Application app = ApplicationManager.getApplication();
    if (!app.isEAP() || PluginManagerCore.isRunningFromSources()) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  private static StreamEx<ThreadInfo> edts(List<ThreadInfo[]> threadInfos) {
    return StreamEx.of(threadInfos)
      .flatMap(Arrays::stream)
      .filter(ThreadDumper::isEDT);
  }

  @Override
  public void uiFreezeStarted() {
    if (!DebugAttachDetector.isAttached()) {
      if (myDumpTask != null) {
        myDumpTask.stop();
      }
      myDumpTask = new SamplingTask(Registry.intValue("freeze.reporter.dump.interval.ms"),
                                    Registry.intValue("freeze.reporter.dump.duration.s") * 1000);
    }
  }

  @Override
  public void dumpedThreads(@NotNull File toFile, @NotNull ThreadDump dump) {
    if (myDumpTask != null) {
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
  public void uiFreezeFinished(long durationMs, @Nullable File reportDir) {
    if (myDumpTask == null) {
      return;
    }
    myDumpTask.stop();
    if (Registry.is("freeze.reporter.enabled")) {
      int lengthInSeconds = (int)(durationMs / 1000);
      long dumpingDuration = durationMs - PerformanceWatcher.getUnresponsiveInterval();
      if (lengthInSeconds > FREEZE_THRESHOLD &&
          // check that we have at least half of the dumps required
          (myDumpTask.isValid(dumpingDuration) ||
           myCurrentDumps.size() >=
           Math.max(3, Math.min(PerformanceWatcher.getMaxDumpDuration(), dumpingDuration / 2) / PerformanceWatcher.getDumpInterval())) &&
          !ContainerUtil.isEmpty(myStacktraceCommonPart)) {
        int size = Math.min(myCurrentDumps.size(), 20); // report up to 20 dumps
        int step = myCurrentDumps.size() / size;
        Attachment[] attachments = new Attachment[size];
        for (int i = 0; i < size; i++) {
          Attachment attachment = new Attachment(DUMP_PREFIX + "-" + i + ".txt", myCurrentDumps.get(i * step).getRawDump());
          attachment.setIncluded(true);
          attachments[i] = attachment;
        }
        IdeaLoggingEvent event = createEvent(lengthInSeconds, attachments, myDumpTask, reportDir);
        if (event != null) {
          Throwable t = event.getThrowable();
          if (IdeErrorsDialog.getSubmitter(t, IdeErrorsDialog.findPluginId(t)) instanceof ITNReporter) { // only report to JB
            MessagePool.getInstance().addIdeFatalMessage(event);
          }
        }
      }
    }
    myDumpTask = null;
    myCurrentDumps.clear();
    myStacktraceCommonPart = null;
  }

  private static ThreadInfo getCauseThread(ThreadInfo[] threadInfos) {
    ThreadInfo edt = ContainerUtil.find(threadInfos, ThreadDumper::isEDT);
    if (edt != null && edt.getThreadState() != Thread.State.RUNNABLE) {
      long id = edt.getLockOwnerId();
      if (id != -1) {
        for (ThreadInfo info : threadInfos) {
          if (info.getThreadId() == id) {
            return info;
          }
        }
      }
      String lockName = edt.getLockName();
      if (lockName != null && lockName.contains("ReadMostlyRWLock")) {
        ThreadInfo readLockNotRunnable = null;
        for (ThreadInfo info : threadInfos) {
          if (isWithReadLock(info)) {
            if (info.getThreadState() == Thread.State.RUNNABLE) {
              return info;
            }
            if (readLockNotRunnable == null) {
              readLockNotRunnable = info;
            }
          }
        }
        if (readLockNotRunnable != null) {
          return readLockNotRunnable;
        }
      }
    }
    return null;
  }

  private static boolean isWithReadLock(ThreadInfo thread) {
    boolean read = false;
    for (StackTraceElement s : thread.getStackTrace()) {
      if ("runReadAction".equals(s.getMethodName()) || "tryRunReadAction".equals(s.getMethodName())) {
        read = true;
      }
      if ("waitABit".equals(s.getMethodName())) {
        return false;
      }
    }
    return read;
  }

  @Nullable
  private IdeaLoggingEvent createEvent(int lengthInSeconds,
                                       Attachment[] attachments,
                                       @NotNull SamplingTask dumpTask,
                                       @Nullable File reportDir) {
    List<ThreadInfo[]> infos = dumpTask.getThreadInfos();
    long time = dumpTask.getDumpInterval();
    if (infos.isEmpty()) {
      infos = StreamEx.of(myCurrentDumps).map(ThreadDump::getThreadInfos).toList();
      time = PerformanceWatcher.getDumpInterval();
    }
    boolean allInEdt = edts(infos)
      .map(ThreadInfo::getThreadState)
      .allMatch(Thread.State.RUNNABLE::equals);
    List<StackTraceElement[]> reasonStacks;
    boolean nonEdt = false;
    if (allInEdt) {
      reasonStacks = edts(infos).map(ThreadInfo::getStackTrace).toList();
    }
    else {
      reasonStacks = new ArrayList<>();
      long causeThreadId = -1;
      for (ThreadInfo[] threadInfos : infos) {
        ThreadDumper.sort(threadInfos); // ensure sorted for better read action matching
        if (causeThreadId == -1) {
          // find probable cause thread
          ThreadInfo thread = getCauseThread(threadInfos);
          if (thread != null) {
            nonEdt = true;
            causeThreadId = thread.getThreadId();
            reasonStacks.add(thread.getStackTrace());
          }
        }
        else {
          for (ThreadInfo info : threadInfos) {
            if (info.getThreadId() == causeThreadId) {
              reasonStacks.add(info.getStackTrace());
            }
          }
        }
      }
    }
    if (reasonStacks.isEmpty()) {
      reasonStacks = edts(infos).map(ThreadInfo::getStackTrace).toList(); // fallback EDT threads
      nonEdt = false;
    }
    CallTreeNode root = CallTreeNode.buildTree(reasonStacks, time);
    List<StackTraceElement> commonStack = root.findDominantCommonStack(reasonStacks.size() * time / 2);
    if (ContainerUtil.isEmpty(commonStack)) {
      commonStack = myStacktraceCommonPart; // fallback to simple EDT common
    }

    String text = root.dump();
    String name = REPORT_PREFIX + "-" + lengthInSeconds + "s.txt";
    Attachment attachment = new Attachment(name, text);
    attachment.setIncluded(true);
    attachments = ArrayUtil.append(attachments, attachment);

    try {
      FileUtil.writeToFile(new File(reportDir, name), text);
    }
    catch (IOException ignored) {
    }

    if (!ContainerUtil.isEmpty(commonStack)) {
      String edtNote = allInEdt ? "in EDT " : "";
      long sampled = dumpTask.getSampledTime();
      long gcTime = dumpTask.getGcTime();
      String message = "Freeze " + edtNote + "for " + lengthInSeconds + " seconds\n" +
                       "Sampled time: " + sampled + "ms, sampling rate: " + dumpTask.getDumpInterval() + "ms";
      if (sampled > 0) {
        message += ", GC time: " + gcTime + "ms (" + gcTime * 100 / sampled + "%)";
      }
      if (DebugAttachDetector.isDebugEnabled()) {
        message += ", debug agent: on";
      }
      if (nonEdt) {
        message += "\n\nThe stack is from the thread that was blocking EDT";
      }
      return LogMessage.createEvent(new Freeze(commonStack), message, attachments);
    }
    return null;
  }

  private static final class CallTreeNode {
    private final StackTraceElement myStackTraceElement;
    private final CallTreeNode myParent;
    private final List<CallTreeNode> myChildren = ContainerUtil.newSmartList();
    private final int myDepth;
    private long myTime;

    static final Comparator<CallTreeNode> TIME_COMPARATOR = Comparator.<CallTreeNode>comparingLong(n -> n.myTime).reversed();

    private CallTreeNode(StackTraceElement element, CallTreeNode parent, long time) {
      myStackTraceElement = element;
      myParent = parent;
      myDepth = parent != null ? parent.myDepth + 1 : 0;
      myTime = time;
    }

    @NotNull
    public static CallTreeNode buildTree(List<StackTraceElement[]> stacks, long time) {
      CallTreeNode root = new CallTreeNode(null, null, 0);
      for (StackTraceElement[] stack : stacks) {
        CallTreeNode node = root;
        for (int i = stack.length - 1; i >= 0; i--) {
          node = node.addCallee(stack[i], time);
        }
      }
      return root;
    }

    CallTreeNode addCallee(StackTraceElement e, long time) {
      for (CallTreeNode child : myChildren) {
        if (PerformanceWatcher.compareStackTraceElements(child.myStackTraceElement, e)) {
          child.myTime += time;
          return child;
        }
      }
      CallTreeNode child = new CallTreeNode(e, this, time);
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
        .append(" ").append(myTime).append("ms").append("\n");
    }

    String dump() {
      StringBuilder sb = new StringBuilder();
      LinkedList<CallTreeNode> nodes = new LinkedList<>(myChildren);
      while (!nodes.isEmpty()) {
        CallTreeNode node = nodes.removeFirst();
        node.appendIndentedString(sb);
        nodes.addAll(0, ContainerUtil.sorted(node.myChildren, TIME_COMPARATOR));
      }
      return sb.toString();
    }

    @Nullable
    private List<StackTraceElement> findDominantCommonStack(long threshold) {
      // find dominant
      CallTreeNode node = getMostHitChild();
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
  }
}
