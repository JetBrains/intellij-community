// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.sun.management.OperatingSystemMXBean;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    "jdk.",
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
    List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    CompilationMXBean jitBean = ManagementFactory.getCompilationMXBean();
    OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    class Monitor implements Runnable {
      private final ActivityMonitorSampler sampler = new ActivityMonitorSampler(this::getSubsystemName);
      private long lastGcTime = totalGcTime();
      private long lastProcessTime = totalProcessTime();
      private long lastJitTime = jitBean.getTotalCompilationTime();
      private long lastUiUpdate = System.currentTimeMillis();

      private final Map<String, String> classToSubsystem = new HashMap<>();

      private static @NotNull String calcSubSystemName(String className) {
        String pkg = StringUtil.getPackageName(className);
        if (pkg.isEmpty()) pkg = className;

        String prefix = getMeaninglessPrefix(pkg);
        if (!prefix.isEmpty()) {
          pkg = pkg.substring(prefix.length()) + " (in " + StringUtil.trimEnd(prefix, ".") + ")";
        }

        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginManager.getPluginByClassNameAsNoAccessToClass(className));
        return plugin == null ? pkg : "Plugin " + plugin.getName() + ": " + pkg;
      }

      private static String getMeaninglessPrefix(String qname) {
        String result = findPrefix(qname, MEANINGLESS_PREFIXES_1);
        if (!result.isEmpty()) {
          result += findPrefix(qname.substring(result.length()), MEANINGLESS_PREFIXES_2);
        }
        return result;
      }

      private static String findPrefix(String qname, String[] prefixes) {
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
        return osBean == null ? 0 : osBean.getProcessCpuTime();
      }

      private @NotNull String getSubsystemName(String threadName, List<String> stack) {
        for (String className : stack) {
          if (className.startsWith(ActivityMonitorAction.class.getName()) || className.startsWith(ActivityMonitorSampler.class.getName())) {
            return "<Activity Monitor>";
          }
          if (!isInfrastructureClass(className)) {
            return classToSubsystem.computeIfAbsent(className, Monitor::calcSubSystemName);
          }
        }
        return "<infrastructure: " + getCommonThreadName(threadName) + ">";
      }

      private static String getCommonThreadName(String name) {
        if (ThreadDumper.isEDT(name)) return "UI thread";

        int numberStart = CharArrayUtil.shiftBackward(name, name.length() - 1, "0123456789/ ") + 1;
        if (numberStart > 0) return name.substring(0, numberStart);
        return name;
      }

      private static boolean isInfrastructureClass(String className) {
        return ContainerUtil.exists(INFRASTRUCTURE_PREFIXES, className::startsWith);
      }

      @Override
      public void run() {
        boolean samplingAvailable = sampler.start();
        long now = System.currentTimeMillis();
        long sinceLastUpdate = now - lastUiUpdate;
        if (sinceLastUpdate > 2_000) {
          lastUiUpdate = now;
          scheduleUiUpdate(sinceLastUpdate, samplingAvailable);
        }
      }

      private void scheduleUiUpdate(long sinceLastUpdate, boolean samplingAvailable) {
        var samples = sampler.takeSamples();
        long totalSamples = samples.values().stream().mapToLong(Long::longValue).sum();
        String activity = samplingAvailable ? samples.entrySet().stream()
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
          .limit(8)
          .map(entry -> String.format("%5.1f  %s", (double)entry.getValue() * 100 / totalSamples, entry.getKey()))
          .collect(Collectors.joining("\n")) : IdeBundle.message("activity.monitor.java.samples.unavailable");
        List<Pair<String, Long>> times = takeSnapshot();
        String text = IdeBundle.message("activity.monitor.java.samples") + "\n\n" + activity + "\n\n" +
                      IdeBundle.message("activity.monitor.cpu.usage") + "\n\n" +
                      times.stream()
                        .filter(p -> p.second > 10)
                        .sorted(Comparator.comparing((Pair<String, Long> p) -> p.second).reversed())
                        .limit(8)
                        .map(p -> String.format("%5.1f  %s", (double)p.second * 100 / sinceLastUpdate, p.first))
                        .collect(Collectors.joining("\n"));
        ApplicationManager.getApplication().invokeLater(() -> {
          textArea.setText(text);
          textArea.setCaretPosition(0);
        }, ModalityState.any());
      }

      private @NotNull List<Pair<String, Long>> takeSnapshot() {
        List<Pair<String, Long>> times = new ArrayList<>();

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
    }
    var monitor = new Monitor();
    ScheduledFuture<?> future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(monitor, 0, 200, TimeUnit.MILLISECONDS);
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
    Disposer.register(dialog.getDisposable(), () -> {
      future.cancel(false);
      monitor.sampler.closeAsync();
    });
    dialog.show();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
