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
  private static final int FREEZE_THRESHOLD = 15; // seconds

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
        if (!allInEdt) {
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
                      myStacktraceCommonPart = ContainerUtil.newArrayList(info.getStackTrace());
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
                myStacktraceCommonPart = PerformanceWatcher.getStacktraceCommonPart(myStacktraceCommonPart, causeThread.getStackTrace());
              }
            }
          }
        }
        if (ContainerUtil.isEmpty(myStacktraceCommonPart)) {
          return null;
        }
        String edtNote = allInEdt ? "in EDT " : "";
        return LogMessage.createEvent(new Freeze(myStacktraceCommonPart),
                                      "Freeze " + edtNote + "for " + lengthInSeconds + " seconds",
                                      attachments);
      }
    });
  }
}
