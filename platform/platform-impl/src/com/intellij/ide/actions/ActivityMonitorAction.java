// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

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
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TLongLongHashMap;
import gnu.trove.TObjectLongHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.management.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class ActivityMonitorAction extends DumbAwareAction {
  private static final String[] MEANINGLESS_PREFIXES_1 = {"com.intellij.", "com.jetbrains.", "org.jetbrains.", "org.intellij."};
  private static final String[] MEANINGLESS_PREFIXES_2 = {"util.", "openapi.", "plugins.", "extapi."};

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    JTextArea textArea = new JTextArea(12, 100);
    textArea.setText("Loading...");
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    CompilationMXBean jitBean = ManagementFactory.getCompilationMXBean();
    ScheduledFuture<?> future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(new Runnable() {
      final TLongLongHashMap lastThreadTimes = new TLongLongHashMap();
      TObjectLongHashMap<String> subsystemToSamples = new TObjectLongHashMap<>();
      long lastGcTime = totalGcTime();
      long lastJitTime = jitBean.getTotalCompilationTime();
      long lastUiUpdate = System.currentTimeMillis();

      private final Map<String, String> classToSubsystem = FactoryMap.create(className -> {
        String pkg = StringUtil.getPackageName(className);
        if (pkg.isEmpty()) pkg = className;

        String prefix = getMeaninglessPrefix(pkg);
        if (!prefix.isEmpty()) {
          pkg = pkg.substring(prefix.length()) + " (in " + StringUtil.trimEnd(prefix, ".") + ")";
        }

        IdeaPluginDescriptor plugin = PluginManager.getPlugin(PluginManagerCore.getPluginByClassName(className));
        return plugin != null ? "Plugin " + plugin.getName() + ": " + pkg : pkg;
      });

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

      @NotNull
      private String getSubsystemName(long threadId) {
        if (threadId == Thread.currentThread().getId()) {
          return "<Activity Monitor>";
        }

        int depth = 50;
        ThreadInfo info = threadBean.getThreadInfo(threadId, depth);
        if (info == null) return "<unidentified: thread finished>";

        if (info.getThreadState() == Thread.State.RUNNABLE) {
          StackTraceElement[] trace = info.getStackTrace();
          for (StackTraceElement element : trace) {
            String className = element.getClassName();
            if (!isInfrastructureClass(className)) {
              return classToSubsystem.get(className);
            }
          }
          if (trace.length == depth) {
            return "<unidentified: too deep stack trace>";
          }
          return "<infrastructure: " + getCommonThreadName(info) + ">";
        }
        return "<unidentified: " + getCommonThreadName(info) + ">";
      }

      private String getCommonThreadName(ThreadInfo info) {
        String name = info.getThreadName();
        if (name.startsWith("AWT-EventQueue")) return "UI thread";

        int numberStart = CharArrayUtil.shiftBackward(name, name.length() - 1, "0123456789/ ") + 1;
        if (numberStart > 0) return name.substring(0, numberStart);
        return name;
      }

      private boolean isInfrastructureClass(String className) {
        return className.startsWith("sun.") ||
               className.startsWith("com.sun.") ||
               className.startsWith("com.yourkit.") ||
               className.startsWith("com.fasterxml.jackson.") ||
               className.startsWith("net.sf.cglib.") ||
               className.startsWith("org.jetbrains.org.objectweb.asm.") ||
               className.startsWith("org.picocontainer.") ||
               className.startsWith("net.jpountz.lz4.") ||
               className.startsWith("net.n3.nanoxml.") ||
               className.startsWith("org.apache.") ||
               className.startsWith("one.util.streamex") ||
               className.startsWith("java.") ||
               className.startsWith("gnu.") ||
               className.startsWith("kotlin.") ||
               className.startsWith("groovy.") ||
               className.startsWith("org.codehaus.groovy.") ||
               className.startsWith("org.gradle.") ||
               className.startsWith("com.google.common.") ||
               className.startsWith("com.google.gson.") ||
               className.startsWith("com.intellij.psi.impl.source.tree.") ||
               className.startsWith("com.intellij.psi.util.Cached") ||
               className.startsWith("com.intellij.openapi.extensions.") ||
               className.startsWith("com.intellij.openapi.util.") ||
               className.startsWith("com.intellij.util.") ||
               className.startsWith("com.intellij.concurrency.") ||
               className.startsWith("com.intellij.psi.stubs.") ||
               className.startsWith("com.intellij.ide.IdeEventQueue") ||
               className.startsWith("com.intellij.openapi.fileTypes.") ||
               className.startsWith("com.intellij.openapi.vfs.newvfs.persistent.PersistentFS") ||
               className.startsWith("com.intellij.openapi.vfs.newvfs.persistent.FSRecords") ||
               className.startsWith("javax.");
      }

      @Override
      public void run() {
        for (long id : threadBean.getAllThreadIds()) {
          long cpuTime = threadBean.getThreadCpuTime(id);
          long prev = lastThreadTimes.put(id, cpuTime);
          if (prev != 0 && cpuTime > prev) {
            String subsystem = getSubsystemName(id);
            subsystemToSamples.put(subsystem, subsystemToSamples.get(subsystem) + cpuTime - prev);
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
        subsystemToSamples.forEachEntry((pkg, time) -> times.add(Pair.create(pkg, TimeUnit.NANOSECONDS.toMillis(time))));
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

      @NotNull
      @Override
      protected Action[] createActions() {
        return new Action[]{getOKAction()};
      }
    };
    dialog.setTitle("Activity Monitor");
    dialog.setModal(false);
    Disposer.register(dialog.getDisposable(), () -> future.cancel(false));
    dialog.show();
  }
}
