// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.management.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class ActivityMonitorAction extends DumbAwareAction {
  private static final @NonNls String[] MEANINGLESS_PREFIXES_1 = {"com.intellij.", "com.jetbrains.", "org.jetbrains.", "org.intellij."};
  private static final @NonNls String[] MEANINGLESS_PREFIXES_2 = {"util.", "openapi.", "plugins.", "extapi."};
  private static final @NonNls String[] INFRASTRUCTURE_PREFIXES = {
    "sun.",
    "com.sun.",
    "com.yourkit.",
    "com.fasterxml.jackson.",
    "net.sf.cglib.",
    "org.jetbrains.org.objectweb.asm.",
    "org.picocontainer.",
    "net.jpountz.lz4.",
    "net.n3.nanoxml.",
    "org.apache.",
    "one.util.streamex",
    "java.",
    "gnu.",
    "kotlin.",
    "groovy.",
    "org.codehaus.groovy.",
    "org.gradle.",
    "com.google.common.",
    "com.google.gson.",
    "com.intellij.openapi.application.impl.",
    "com.intellij.psi.impl.",
    "com.intellij.extapi.psi.",
    "com.intellij.psi.util.Cached",
    "com.intellij.openapi.extensions.",
    "com.intellij.openapi.util.",
    "com.intellij.facet.",
    "com.intellij.util.",
    "com.intellij.concurrency.",
    "com.intellij.semantic.",
    "com.intellij.serviceContainer.",
    "com.intellij.jam.",
    "com.intellij.psi.stubs.",
    "com.intellij.openapi.progress.impl.",
    "com.intellij.ide.IdeEventQueue",
    "com.intellij.openapi.fileTypes.",
    "com.intellij.openapi.vfs.newvfs.persistent.PersistentFS",
    "com.intellij.openapi.vfs.newvfs.persistent.FSRecords",
    "com.intellij.openapi.roots.impl",
    "javax."
  };

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    JTextArea textArea = new JTextArea(12, 100);
    textArea.setText(CommonBundle.getLoadingTreeNodeText());
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    CompilationMXBean jitBean = ManagementFactory.getCompilationMXBean();
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    Method getProcessCpuTime = Objects.requireNonNull(ReflectionUtil.getMethod(osBean.getClass().getInterfaces()[0], "getProcessCpuTime"));
    ScheduledFuture<?> future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(new Runnable() {
      final Long2LongMap lastThreadTimes = new Long2LongOpenHashMap();
      final Object2LongMap<String> subsystemToSamples = new Object2LongOpenHashMap<>();
      long lastGcTime = totalGcTime();
      long lastProcessTime = totalProcessTime();
      long lastJitTime = jitBean.getTotalCompilationTime();
      long lastUiUpdate = System.currentTimeMillis();

      private final Map<String, String> classToSubsystem = new HashMap<>();

      @NotNull
      private String calcSubSystemName(String className) {
        String pkg = StringUtil.getPackageName(className);
        if (pkg.isEmpty()) pkg = className;

        String prefix = getMeaninglessPrefix(pkg);
        if (!prefix.isEmpty()) {
          pkg = pkg.substring(prefix.length()) + " (in " + StringUtil.trimEnd(prefix, ".") + ")";
        }

        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginManager.getPluginByClassNameAsNoAccessToClass(className));
        return plugin == null ? pkg : "Plugin " + plugin.getName() + ": " + pkg;
      }

      private String getMeaninglessPrefix(String qname) {
        String result = findPrefix(qname, MEANINGLESS_PREFIXES_1);
        if (!result.isEmpty()) {
          result += findPrefix(qname.substring(result.length()), MEANINGLESS_PREFIXES_2);
        }
        return result;
      }

      private String findPrefix(String qname, String[] prefixes) {
        for (String prefix : prefixes) {
          if (qname.startsWith(prefix)) {
            return prefix;
          }
        }
        return "";
      }

      private long totalGcTime() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
      }

      private long totalProcessTime() {
        try {
          return (long)getProcessCpuTime.invoke(osBean);
        }
        catch (Exception ex) {
          return 0;
        }
      }

      @NotNull
      private String getSubsystemName(long threadId) {
        if (threadId == Thread.currentThread().getId()) {
          return "<Activity Monitor>";
        }

        ThreadInfo info = threadBean.getThreadInfo(threadId, Integer.MAX_VALUE);
        if (info == null) return "<unidentified: thread finished>";

        boolean runnable = info.getThreadState() == Thread.State.RUNNABLE;
        if (runnable) {
          for (StackTraceElement element : info.getStackTrace()) {
            String className = element.getClassName();
            if (!isInfrastructureClass(className)) {
              return classToSubsystem.computeIfAbsent(className, this::calcSubSystemName);
            }
          }
        }
        return (runnable ? "<infrastructure: " : "<unidentified: ") + getCommonThreadName(info) + ">";
      }

      private String getCommonThreadName(ThreadInfo info) {
        String name = info.getThreadName();
        if (ThreadDumper.isEDT(name)) return "UI thread";

        int numberStart = CharArrayUtil.shiftBackward(name, name.length() - 1, "0123456789/ ") + 1;
        if (numberStart > 0) return name.substring(0, numberStart);
        return name;
      }

      private boolean isInfrastructureClass(String className) {
        return ContainerUtil.exists(INFRASTRUCTURE_PREFIXES, className::startsWith);
      }

      @Override
      public void run() {
        for (long id : threadBean.getAllThreadIds()) {
          long cpuTime = threadBean.getThreadCpuTime(id);
          long prev = lastThreadTimes.put(id, cpuTime);
          if (prev != 0 && cpuTime > prev) {
            String subsystem = getSubsystemName(id);
            subsystemToSamples.put(subsystem, subsystemToSamples.getLong(subsystem) + cpuTime - prev);
          }
        }
        long now = System.currentTimeMillis();
        long sinceLastUpdate = now - lastUiUpdate;
        if (sinceLastUpdate > 2_000) {
          lastUiUpdate = now;
          scheduleUiUpdate(sinceLastUpdate);
        }
      }

      private void scheduleUiUpdate(long sinceLastUpdate) {
        List<Pair<String, Long>> times = takeSnapshot();
        String text = " %CPU  Subsystem\n\n" +
                      StreamEx.of(times)
                        .filter(p -> p.second > 10)
                        .sorted(Comparator.comparing((Pair<String, Long> p) -> p.second).reversed())
                        .limit(8)
                        .map(p -> String.format("%5.1f  %s", (double) p.second*100 / sinceLastUpdate, p.first))
                        .joining("\n");
        ApplicationManager.getApplication().invokeLater(() -> {
          textArea.setText(text);
          textArea.setCaretPosition(0);
        }, ModalityState.any());
      }

      @NotNull
      private List<Pair<String, Long>> takeSnapshot() {
        List<Pair<String, Long>> times = new ArrayList<>();
        for (Object2LongMap.Entry<String> entry : subsystemToSamples.object2LongEntrySet()) {
          times.add(new Pair<>(entry.getKey(), TimeUnit.NANOSECONDS.toMillis(entry.getLongValue())));
        }
        subsystemToSamples.clear();

        long gcTime = totalGcTime();
        if (gcTime != lastGcTime) {
          times.add(Pair.create("<Garbage collection>", gcTime - lastGcTime));
          lastGcTime = gcTime;
        }

        long jitTime = jitBean.getTotalCompilationTime();
        if (jitTime != lastJitTime) {
          times.add(Pair.create("<JIT compiler>", jitTime - lastJitTime));
          lastJitTime = jitTime;
        }

        long processTime = totalProcessTime();
        if (processTime != lastProcessTime) {
          times.add(Pair.create("<Process total CPU usage>", TimeUnit.NANOSECONDS.toMillis(processTime - lastProcessTime)));
          lastProcessTime = processTime;
        }

        return times;
      }

    }, 0, 20, TimeUnit.MILLISECONDS);
    DialogWrapper dialog = new DialogWrapper(false) {
      {
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        JBScrollPane pane = new JBScrollPane(textArea);
        pane.setPreferredSize(textArea.getPreferredSize());
        return pane;
      }

      @Override
      protected String getDimensionServiceKey() {
        return "Performance.Activity.Monitor";
      }

      @Override
      protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
      }
    };
    dialog.setTitle(IdeBundle.message("dialog.title.activity.monitor"));
    dialog.setModal(false);
    Disposer.register(dialog.getDisposable(), () -> future.cancel(false));
    dialog.show();
  }
}
