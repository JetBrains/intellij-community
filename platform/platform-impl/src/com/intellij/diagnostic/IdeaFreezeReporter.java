// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.management.ThreadInfo;
import java.util.*;
import java.util.function.Function;

final class IdeaFreezeReporter implements IdePerformanceListener {
  private static final int FREEZE_THRESHOLD = ApplicationManager.getApplication().isInternal() ? 15 : 25; // seconds
  private static final String REPORT_PREFIX = "report";
  private static final String DUMP_PREFIX = "dump";
  private static final String MESSAGE_FILE_NAME = ".message";
  private static final String THROWABLE_FILE_NAME = ".throwable";
  public static final String APPINFO_FILE_NAME = ".appinfo";
  // common sub-stack contains more than the specified % samples
  private static final double COMMON_SUB_STACK_WEIGHT = 0.25;

  @SuppressWarnings("FieldMayBeFinal")
  private static boolean DEBUG = false;

  private SamplingTask myDumpTask;
  private final List<ThreadDump> myCurrentDumps = new ArrayList<>();
  private List<StackTraceElement> myStacktraceCommonPart = null;
  private volatile boolean myAppClosing;

  IdeaFreezeReporter() {
    Application app = ApplicationManager.getApplication();
    if (!DEBUG && PluginManagerCore.isRunningFromSources() || (!app.isEAP() && !app.isInternal())) {
      throw ExtensionNotApplicableException.INSTANCE;
    }

    NonUrgentExecutor.getInstance().execute(() -> {
      app.getMessageBus().simpleConnect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
        @Override
        public void appWillBeClosed(boolean isRestart) {
          myAppClosing = true;
        }
      });

      PerformanceWatcher.getInstance().processUnfinishedFreeze((dir, duration) -> {
        try {
          // report deadly freeze
          File[] files = dir.listFiles();
          if (files != null) {
            if (duration > FREEZE_THRESHOLD) {
              List<Attachment> attachments = new ArrayList<>();
              String message = null;
              String appInfo = null;
              Throwable throwable = null;
              List<String> dumps = new ArrayList<>();
              for (File file : files) {
                String text = FileUtil.loadFile(file);
                String name = file.getName();
                if (MESSAGE_FILE_NAME.equals(name)) {
                  message = text;
                }
                else if (THROWABLE_FILE_NAME.equals(name)) {
                  try (FileInputStream fis = new FileInputStream(file); ObjectInputStream ois = new ObjectInputStream(fis)) {
                    throwable = (Throwable)ois.readObject();
                  }
                  catch (Exception ignored) {
                  }
                }
                else if (APPINFO_FILE_NAME.equals(name)) {
                  appInfo = text;
                }
                else if (name.startsWith(REPORT_PREFIX)) {
                  attachments.add(createReportAttachment(duration, text));
                }
                else if (name.startsWith(PerformanceWatcher.DUMP_PREFIX)) {
                  dumps.add(text);
                }
              }

              addDumpsAttachments(dumps, Function.identity(), attachments);

              if (message != null && throwable != null && !attachments.isEmpty()) {
                IdeaLoggingEvent event = LogMessage.createEvent(throwable, message, attachments.toArray(Attachment.EMPTY_ARRAY));
                setAppInfo(event, appInfo);
                report(event);
              }
            }
            cleanup(dir);
          }
        }
        catch (IOException ignored) {
        }
      });
    });
  }

  static void setAppInfo(IdeaLoggingEvent event, String appInfo) {
    Object data = event.getData();
    if (data instanceof AbstractMessage) {
      ((AbstractMessage)data).setAppInfo(appInfo);
    }
  }

  private static Attachment createReportAttachment(int lengthInSeconds, String text) {
    Attachment res = new Attachment(REPORT_PREFIX + "-" + lengthInSeconds + "s.txt", text);
    res.setIncluded(true);
    return res;
  }

  // get 20 scattered elements
  private static <T> void addDumpsAttachments(List<T> from, Function<? super T, String> textMapper, List<? super Attachment> container) {
    int size = Math.min(from.size(), 20);
    int step = from.size() / size;
    for (int i = 0; i < size; i++) {
      Attachment attachment = new Attachment(DUMP_PREFIX + "-" + i + ".txt", textMapper.apply(from.get(i * step)));
      attachment.setIncluded(true);
      container.add(attachment);
    }
  }

  private static void cleanup(@Nullable File dir) {
    if (dir != null) {
      FileUtil.delete(new File(dir, MESSAGE_FILE_NAME));
      FileUtil.delete(new File(dir, THROWABLE_FILE_NAME));
      FileUtil.delete(new File(dir, APPINFO_FILE_NAME));
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
      File dir = toFile.getParentFile();
      IdeaLoggingEvent event = createEvent((int)((myDumpTask.getTotalTime() + PerformanceWatcher.getUnresponsiveInterval()) / 1000),
                                           Collections.emptyList(), myDumpTask, dir, false);
      if (event != null) {
        try {
          FileUtil.writeToFile(new File(dir, MESSAGE_FILE_NAME), event.getMessage());
          try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(dir, THROWABLE_FILE_NAME)))) {
            oos.writeObject(event.getThrowable());
          }
          saveAppInfo(dir, false);
        }
        catch (IOException ignored) { }
      }
    }
  }

  static void saveAppInfo(File dir, boolean overwrite) throws IOException {
    File appInfoFile = new File(dir, APPINFO_FILE_NAME);
    if (overwrite || !appInfoFile.exists()) {
      FileUtil.writeToFile(appInfoFile, ITNProxy.getAppInfoString());
    }
  }

  @Override
  public void uiFreezeFinished(long durationMs, @Nullable File reportDir) {
    if (myDumpTask == null) {
      return;
    }
    myDumpTask.stop();

    cleanup(reportDir);

    if (Registry.is("freeze.reporter.enabled")) {
      int lengthInSeconds = (int)(durationMs / 1000);
      long dumpingDuration = durationMs - PerformanceWatcher.getUnresponsiveInterval();
      if (lengthInSeconds > FREEZE_THRESHOLD &&
          // check that we have at least half of the dumps required
          (myDumpTask.isValid(dumpingDuration) ||
           myCurrentDumps.size() >=
           Math.max(3, Math.min(PerformanceWatcher.getMaxDumpDuration(), dumpingDuration / 2) / PerformanceWatcher.getDumpInterval())) &&
          !ContainerUtil.isEmpty(myStacktraceCommonPart)) {
        List<Attachment> attachments = new ArrayList<>();
        addDumpsAttachments(myCurrentDumps, ThreadDump::getRawDump, attachments);

        report(createEvent(lengthInSeconds, attachments, myDumpTask, reportDir, true));
      }
    }
    myDumpTask = null;
    reset();
  }

  static void report(IdeaLoggingEvent event) {
    if (event != null) {
      Throwable t = event.getThrowable();
      if (IdeErrorsDialog.getSubmitter(t, PluginUtil.getInstance().findPluginId(t)) instanceof ITNReporter) { // only report to JB
        MessagePool.getInstance().addIdeFatalMessage(event);
      }
    }
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
      String methodName = s.getMethodName();
      if ("runReadAction".equals(methodName) || "tryRunReadAction".equals(methodName) || "insideReadAction".equals(methodName)) {
        read = true;
      }
      if ("waitABit".equals(methodName)) {
        return false;
      }
    }
    return read;
  }

  private @Nullable IdeaLoggingEvent createEvent(int lengthInSeconds,
                                                 List<Attachment> attachments,
                                                 @NotNull SamplingTask dumpTask,
                                                 @Nullable File reportDir,
                                                 boolean finished) {
    List<ThreadInfo[]> infos = dumpTask.getThreadInfos();
    long dumpInterval = dumpTask.getDumpInterval();
    long sampledTime = dumpTask.getSampledTime();
    if (infos.isEmpty()) {
      infos = ContainerUtil.map(myCurrentDumps, ThreadDump::getThreadInfos);
      dumpInterval = PerformanceWatcher.getDumpInterval();
      sampledTime = infos.size() * dumpInterval;
    }

    List<ThreadInfo> causeThreads = ContainerUtil.mapNotNull(infos, IdeaFreezeReporter::getCauseThread);
    boolean allInEdt = causeThreads.stream().allMatch(ThreadDumper::isEDT);

    CallTreeNode root = CallTreeNode.buildTree(causeThreads, dumpInterval);
    int classLoadingRatio = countClassLoading(causeThreads) * 100 / causeThreads.size();
    CallTreeNode commonStackNode = root.findDominantCommonStack((long)(causeThreads.size() * dumpInterval * COMMON_SUB_STACK_WEIGHT));
    List<StackTraceElement> commonStack = commonStackNode != null ? commonStackNode.getStack() : null;

    boolean nonEdtCause = false;
    if (ContainerUtil.isEmpty(commonStack)) {
      commonStack = myStacktraceCommonPart; // fallback to simple EDT common
    }
    else {
      nonEdtCause = !ThreadDumper.isEDT(commonStackNode.myThreadInfo);
    }

    String reportText = root.dump();

    try {
      if (reportDir != null) {
        FileUtil.writeToFile(new File(reportDir, REPORT_PREFIX + ".txt"), reportText);
      }
    }
    catch (IOException ignored) {
    }

    if (!ContainerUtil.isEmpty(commonStack)) {
      if (commonStack.stream().anyMatch(IdeaFreezeReporter::skippedFrame)) {
        return null;
      }

      String edtNote = allInEdt ? "in EDT " : "";
      String message = "Freeze " + edtNote + "for " + lengthInSeconds + " seconds\n" +
                       (finished ? "" : myAppClosing ? "IDE is closing. " : "IDE KILLED! ") +
                       "Sampled time: " + sampledTime + "ms, sampling rate: " + dumpInterval + "ms";
      String jitProblem = PerformanceWatcher.getInstance().getJitProblem();
      if (jitProblem != null) {
        message += ", " + jitProblem;
      }
      long total = dumpTask.getTotalTime();
      long gcTime = dumpTask.getGcTime();
      if (total > 0) {
        message += ", GC time: " + gcTime + "ms (" + gcTime * 100 / total + "%), Class loading: " + classLoadingRatio + "%";
      }
      if (DebugAttachDetector.isDebugEnabled()) {
        message += ", debug agent: on";
      }
      double averageLoad = dumpTask.getOsAverageLoad();
      if (averageLoad > 0) {
        message += ", load average: " + String.format("%.2f", averageLoad);
      }
      if (nonEdtCause) {
        message += "\n\nThe stack is from the thread that was blocking EDT";
      }
      Attachment report = createReportAttachment(lengthInSeconds, reportText);
      return LogMessage.createEvent(new Freeze(commonStack), message,
                                    ContainerUtil.append(attachments, report).toArray(Attachment.EMPTY_ARRAY));
    }
    return null;
  }

  private static boolean skippedFrame(StackTraceElement e) {
    return ApplicationImpl.class.getName().equals(e.getClassName()) && "runEdtProgressWriteAction".equals(e.getMethodName());
  }

  private static int countClassLoading(List<? extends ThreadInfo> causeThreads) {
    return (int)causeThreads.stream().filter(t -> Arrays.stream(t.getStackTrace()).anyMatch(IdeaFreezeReporter::isClassLoading)).count();
  }

  private static boolean isClassLoading(StackTraceElement stackTraceElement) {
    return "loadClass".equals(stackTraceElement.getMethodName()) && "java.lang.ClassLoader".equals(stackTraceElement.getClassName());
  }

  private static final class CallTreeNode {
    private final StackTraceElement myStackTraceElement;
    private final CallTreeNode myParent;
    private final List<CallTreeNode> myChildren = new SmartList<>();
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

    public static @NotNull CallTreeNode buildTree(List<? extends ThreadInfo> threadInfos, long time) {
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

    private @Nullable CallTreeNode findDominantCommonStack(long threshold) {
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
