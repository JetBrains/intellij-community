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
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class IdeaFreezeReporter implements IdePerformanceListener {
  private static final int FREEZE_THRESHOLD = ApplicationManager.getApplication().isInternal() ? 5 : 25; // seconds
  private static final String REPORT_PREFIX = "report";
  private static final String DUMP_PREFIX = "dump";

  private volatile DumpTask myDumpTask;
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

  @Nullable
  private static List<StackTraceElement> findDominantCommonStack(CallTreeNode root, long threshold) {
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
  private static CallTreeNode buildTree(List<StackTraceElement[]> stacks, long time) {
    CallTreeNode root = new CallTreeNode(null, null, 0, 0);
    for (StackTraceElement[] stack : stacks) {
      CallTreeNode node = root;
      for (int i = stack.length - 1; i >= 0; i--) {
        node = node.addCallee(stack[i], time);
      }
    }
    return root;
  }

  private static final class CallTreeNode {
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
        .append(" ").append(myTime).append("ms").append("\n");
    }
  }

  private static final class DumpTask {
    private final int myDumpInterval;
    private final int myMaxDumps;
    private final ScheduledFuture<?> myFuture;
    private final List<ThreadInfo[]> myThreadInfos = new ArrayList<>();
    private final static ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();
    private final static List<GarbageCollectorMXBean> GC_MX_BEANS = ManagementFactory.getGarbageCollectorMXBeans();
    private final long myStartTime;
    private long myCurrentTime;
    private final long myGcStartTime;
    private long myGcCurrentTime;

    private DumpTask() {
      myDumpInterval = Registry.intValue("freeze.reporter.dump.interval.ms");
      myMaxDumps = Registry.intValue("freeze.reporter.dump.duration.s") * 1000 / myDumpInterval;
      myCurrentTime = myStartTime = System.currentTimeMillis();
      myGcCurrentTime = myGcStartTime = currentGcTime();
      ScheduledExecutorService executor = PerformanceWatcher.getInstance().getExecutor();
      myFuture = executor.scheduleWithFixedDelay(this::dumpThreads, 0, myDumpInterval, TimeUnit.MILLISECONDS);
    }

    void dumpThreads() {
      myCurrentTime = System.currentTimeMillis();
      myGcCurrentTime = currentGcTime();
      ThreadInfo[] infos = ThreadDumper.getThreadInfos(THREAD_MX_BEAN, false);
      myThreadInfos.add(infos);
      if (myThreadInfos.size() >= myMaxDumps) {
        cancel();
      }
    }

    private static long currentGcTime() {
      return GC_MX_BEANS.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    long getSampledTime() {
      return myCurrentTime - myStartTime;
    }

    long getGcTime() {
      return myGcCurrentTime - myGcStartTime;
    }

    boolean isValid(long dumpingDuration) {
      return myThreadInfos.size() >= Math.max(10, Math.min(myMaxDumps, dumpingDuration / myDumpInterval / 2));
    }

    void cancel() {
      myFuture.cancel(false);
    }
  }

  @Override
  public void uiFreezeStarted() {
    if (Registry.is("freeze.reporter.enabled") && !DebugAttachDetector.isAttached()) {
      if (myDumpTask != null) {
        myDumpTask.cancel();
      }
      myDumpTask = new DumpTask();
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
    myDumpTask.cancel();
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
    myDumpTask = null;
    myCurrentDumps.clear();
    myStacktraceCommonPart = null;
  }

  @Nullable
  private IdeaLoggingEvent createEvent(int lengthInSeconds,
                                       Attachment[] attachments,
                                       @NotNull DumpTask dumpTask,
                                       @Nullable File reportDir) {
    List<ThreadInfo[]> infos = dumpTask.myThreadInfos;
    long time = dumpTask.myDumpInterval;
    if (infos.isEmpty()) {
      infos = StreamEx.of(myCurrentDumps).map(ThreadDump::getThreadInfos).toList();
      time = PerformanceWatcher.getDumpInterval();
    }
    boolean allInEdt = edts(infos)
      .map(ThreadInfo::getThreadState)
      .allMatch(Thread.State.RUNNABLE::equals);
    List<StackTraceElement[]> reasonStacks;
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
          ThreadInfo causeThread = ContainerUtil.find(threadInfos, i -> i.getThreadId() == finalCauseThreadId);
          if (causeThread != null) {
            reasonStacks.add(causeThread.getStackTrace());
          }
        }
      }
    }
    if (reasonStacks.isEmpty()) {
      reasonStacks = edts(infos).map(ThreadInfo::getStackTrace).toList(); // fallback EDT threads
    }
    CallTreeNode root = buildTree(reasonStacks, time);
    List<StackTraceElement> commonStack = findDominantCommonStack(root, reasonStacks.size() * time / 2);
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
    String text = sb.toString();
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
                       "Sampled time: " + sampled + "ms";
      if (sampled > 0) {
        message += "\nGC time: " + gcTime + "ms (" + gcTime * 100 / sampled + "%)";
      }
      return LogMessage.createEvent(new Freeze(commonStack), message, attachments);
    }
    return null;
  }
}
