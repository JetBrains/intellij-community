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

  @SuppressWarnings("FieldMayBeFinal") private static boolean DEBUG = false;

  private SamplingTask myDumpTask;
  final List<ThreadDump> myCurrentDumps = new ArrayList<>();
  List<StackTraceElement> myStacktraceCommonPart = null;

  IdeaFreezeReporter() {
    Application app = ApplicationManager.getApplication();
    if (!DEBUG && (!app.isEAP() || PluginManagerCore.isRunningFromSources())) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @Override
  public void uiFreezeStarted() {
    if (DEBUG || !DebugAttachDetector.isAttached()) {
      if (myDumpTask != null) {
        myDumpTask.stop();
      }
      reset();
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
    reset();
  }

  private void reset() {
    myCurrentDumps.clear();
    myStacktraceCommonPart = null;
  }

  private static ThreadInfo getCauseThread(ThreadInfo[] threadInfos) {
    ThreadDumper.sort(threadInfos); // ensure sorted for better read action matching
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
    return edt;
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

    List<ThreadInfo> causeThreads = StreamEx.of(infos).map(IdeaFreezeReporter::getCauseThread).nonNull().toList();
    boolean allInEdt = causeThreads.stream().allMatch(ThreadDumper::isEDT);

    CallTreeNode root = CallTreeNode.buildTree(causeThreads, time);
    int classLoadingRatio = countClassLoading(causeThreads) * 100 / causeThreads.size();
    CallTreeNode commonStackNode = root.findDominantCommonStack(causeThreads.size() * time / 2);
    List<StackTraceElement> commonStack = commonStackNode != null ? commonStackNode.getStack() : null;

    boolean nonEdtCause = false;
    if (ContainerUtil.isEmpty(commonStack)) {
      commonStack = myStacktraceCommonPart; // fallback to simple EDT common
    }
    else {
      nonEdtCause = !ThreadDumper.isEDT(commonStackNode.myThreadInfo);
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
      String message = "Freeze " + edtNote + "for " + lengthInSeconds + " seconds\n" +
                       "Sampled time: " + dumpTask.getSampledTime() + "ms, sampling rate: " + dumpTask.getDumpInterval() + "ms";
      long total = dumpTask.getTotalTime();
      long gcTime = dumpTask.getGcTime();
      if (total > 0) {
        message += ", GC time: " + gcTime + "ms (" + gcTime * 100 / total + "%), Class loading: " + classLoadingRatio + "%";
      }
      if (DebugAttachDetector.isDebugEnabled()) {
        message += ", debug agent: on";
      }
      if (nonEdtCause) {
        message += "\n\nThe stack is from the thread that was blocking EDT";
      }
      return LogMessage.createEvent(new Freeze(commonStack), message, attachments);
    }
    return null;
  }

  private static int countClassLoading(List<ThreadInfo> causeThreads) {
    return (int)causeThreads.stream().filter(t -> Arrays.stream(t.getStackTrace()).anyMatch(IdeaFreezeReporter::isClassLoading)).count();
  }

  private static boolean isClassLoading(StackTraceElement stackTraceElement) {
    return "loadClass".equals(stackTraceElement.getMethodName()) && "java.lang.ClassLoader".equals(stackTraceElement.getClassName());
  }

  private static final class CallTreeNode {
    private final StackTraceElement myStackTraceElement;
    private final CallTreeNode myParent;
    private final List<CallTreeNode> myChildren = ContainerUtil.newSmartList();
    private final int myDepth;
    private long myTime;
    private final ThreadInfo myThreadInfo;

    static final Comparator<CallTreeNode> TIME_COMPARATOR = Comparator.<CallTreeNode>comparingLong(n -> n.myTime).reversed();

    private CallTreeNode(StackTraceElement element, CallTreeNode parent, long time, ThreadInfo info) {
      myStackTraceElement = element;
      myParent = parent;
      myDepth = parent != null ? parent.myDepth + 1 : 0;
      myTime = time;
      myThreadInfo = info;
    }

    @NotNull
    public static CallTreeNode buildTree(List<ThreadInfo> threadInfos, long time) {
      CallTreeNode root = new CallTreeNode(null, null, 0, null);
      for (ThreadInfo thread : threadInfos) {
        CallTreeNode node = root;
        StackTraceElement[] stack = thread.getStackTrace();
        for (int i = stack.length - 1; i >= 0; i--) {
          node = node.addCallee(stack[i], time, thread);
        }
      }
      return root;
    }

    CallTreeNode addCallee(StackTraceElement e, long time, ThreadInfo threadInfo) {
      for (CallTreeNode child : myChildren) {
        if (PerformanceWatcher.compareStackTraceElements(child.myStackTraceElement, e)) {
          child.myTime += time;
          return child;
        }
      }
      CallTreeNode child = new CallTreeNode(e, this, time, threadInfo);
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

    private List<StackTraceElement> getStack() {
      List<StackTraceElement> res = new ArrayList<>();
      CallTreeNode node = this;
      while (node != null && node.myStackTraceElement != null) {
        res.add(node.myStackTraceElement);
        node = node.myParent;
      }
      return res;
    }

    @Nullable
    private CallTreeNode findDominantCommonStack(long threshold) {
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
      return node;
    }
  }
}
