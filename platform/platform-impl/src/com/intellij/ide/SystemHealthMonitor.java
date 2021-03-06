// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.diagnostic.VMOptions;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.ide.actions.EditCustomVmOptionsAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.jna.JnaLoader;
import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationFullContent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.MathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.system.CpuArch;
import com.sun.jna.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class SystemHealthMonitor extends PreloadingActivity {
  private static final Logger LOG = Logger.getInstance(SystemHealthMonitor.class);
  private static final String DISPLAY_ID = "System Health";

  @Override
  public void preload(@NotNull ProgressIndicator indicator) {
    checkInstallationIntegrity();
    checkIdeDirectories();
    checkRuntime();
    checkReservedCodeCacheSize();
    checkEnvironment();
    checkSignalBlocking();
    startDiskSpaceMonitoring();
  }

  private static void checkInstallationIntegrity() {
    if (SystemInfo.isUnix && !SystemInfo.isMac) {
      try (Stream<Path> stream = Files.list(Path.of(PathManager.getLibPath()))) {
        // see `LinuxDistributionBuilder#generateVersionMarker`
        long markers = stream.filter(p -> p.getFileName().toString().startsWith("build-marker-")).count();
        if (markers > 1) {
          showNotification("mixed.bag.installation", false, null, ApplicationNamesInfo.getInstance().getFullProductName());
        }
      }
      catch (IOException e) {
        LOG.warn(e.getClass().getName() + ": " + e.getMessage());
      }
    }
  }

  private static void checkIdeDirectories() {
    if (System.getProperty(PathManager.PROPERTY_PATHS_SELECTOR) != null) {
      if (System.getProperty(PathManager.PROPERTY_CONFIG_PATH) != null && System.getProperty(PathManager.PROPERTY_PLUGINS_PATH) == null) {
        showNotification("implicit.plugin.directory.path", null, shorten(PathManager.getPluginsPath()));
      }
      if (System.getProperty(PathManager.PROPERTY_SYSTEM_PATH) != null && System.getProperty(PathManager.PROPERTY_LOG_PATH) == null) {
        showNotification("implicit.log.directory.path", null, shorten(PathManager.getLogPath()));
      }
    }
  }

  private static String shorten(String pathStr) {
    Path path = Path.of(pathStr).toAbsolutePath(), userHome = Path.of(SystemProperties.getUserHome());
    if (path.startsWith(userHome)) {
      Path relative = userHome.relativize(path);
      return SystemInfo.isWindows ? "%USERPROFILE%\\" + relative : "~/" + relative;
    }
    else {
      return pathStr;
    }
  }

  private static void checkRuntime() {
    String jreHome = SystemProperties.getJavaHome();
    if (!(PathManager.isUnderHomeDirectory(jreHome) || isModernJBR())) {
      // the JRE is non-bundled and is either non-JB or older than bundled
      NotificationAction switchAction = null;

      String directory = PathManager.getCustomOptionsDirectory();
      if (directory != null && (SystemInfo.isWindows || SystemInfo.isMac || SystemInfo.isLinux) && isJbrOperational()) {
        String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
        String configName = scriptName + (!SystemInfo.isWindows ? "" : CpuArch.isIntel64() ? "64.exe" : ".exe") + ".jdk";
        Path configFile = Path.of(directory, configName);
        if (Files.isRegularFile(configFile)) {
          switchAction = new NotificationAction(IdeBundle.message("action.SwitchToJBR.text")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
              notification.expire();
              try {
                Files.delete(configFile);
              }
              catch (IOException x) {
                LOG.warn("Can't delete JDK configuration file: " + configFile, x);
              }
              ApplicationManager.getApplication().restart();
            }
          };
        }
      }

      jreHome = StringUtil.trimEnd(jreHome, "/Contents/Home");
      showNotification("bundled.jre.version.message", switchAction, JavaVersion.current(), SystemInfo.JAVA_VENDOR, jreHome);
    }
  }

  private static boolean isModernJBR() {
    if (!SystemInfo.isJetBrainsJvm) {
      return false;
    }
    // when can't detect a JBR version, give a user the benefit of the doubt
    JdkVersionDetector.JdkVersionInfo jbrVersion = JdkVersionDetector.getInstance().detectJdkVersionInfo(PathManager.getBundledRuntimePath());
    return jbrVersion == null || JavaVersion.current().compareTo(jbrVersion.version) >= 0;
  }

  private static boolean isJbrOperational() {
    Path bin = Path.of(PathManager.getBundledRuntimePath(), SystemInfo.isWindows ? "bin/java.exe": "bin/java");
    if (Files.isRegularFile(bin) && (SystemInfo.isWindows || Files.isExecutable(bin))) {
      try {
        return new CapturingProcessHandler(new GeneralCommandLine(bin.toString(), "-version")).runProcess(30_000).getExitCode() == 0;
      }
      catch (ExecutionException e) {
        LOG.debug(e);
      }
    }

    return false;
  }

  private static void checkReservedCodeCacheSize() {
    int reservedCodeCacheSize = VMOptions.readOption(VMOptions.MemoryKind.CODE_CACHE, true);
    int minReservedCodeCacheSize = 240;  //todo[r.sh] PluginManagerCore.isRunningFromSources() ? 240 : CpuArch.is32Bit() ? 384 : 512;
    if (reservedCodeCacheSize > 0 && reservedCodeCacheSize < minReservedCodeCacheSize) {
      EditCustomVmOptionsAction vmEditAction = new EditCustomVmOptionsAction();
      NotificationAction action = new NotificationAction(IdeBundle.message("vm.options.edit.action.cap")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          notification.expire();
          ActionUtil.performActionDumbAware(vmEditAction, e);
        }
      };
      showNotification("code.cache.warn.message", vmEditAction.isEnabled() ? action : null, reservedCodeCacheSize, minReservedCodeCacheSize);
    }
  }

  private static void checkEnvironment() {
    List<String> usedVars = Stream.of("_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS")
      .filter(var -> Strings.isNotEmpty(System.getenv(var)))
      .collect(Collectors.toList());
    if (!usedVars.isEmpty()) {
      showNotification("vm.options.env.vars", null, String.join(", ", usedVars));
    }
  }

  private static void checkSignalBlocking() {
    if (SystemInfo.isUnix & JnaLoader.isLoaded()) {
      try {
        Memory sa = new Memory(256);
        LibC libC = Native.load("c", LibC.class);
        if (libC.sigaction(UnixProcessManager.SIGINT, Pointer.NULL, sa) == 0 && LibC.SIG_IGN.equals(sa.getPointer(0))) {
          libC.signal(UnixProcessManager.SIGINT, LibC.Handler.TERMINATE);
          LOG.info("restored ignored INT handler");
        }
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    }
  }

  private static void showNotification(@PropertyKey(resourceBundle = "messages.IdeBundle") String key,
                                       @Nullable NotificationAction action,
                                       Object... params) {
    showNotification(key, true, action, params);
  }

  private static void showNotification(@PropertyKey(resourceBundle = "messages.IdeBundle") String key,
                                       boolean suppressable,
                                       @Nullable NotificationAction action,
                                       Object... params) {
    if (suppressable) {
      boolean ignored = PropertiesComponent.getInstance().isValueSet("ignore." + key);
      LOG.warn("issue detected: " + key + (ignored ? " (ignored)" : ""));
      if (ignored) return;
    }

    Notification notification = new MyNotification(IdeBundle.message(key, params));
    if (action != null) {
      notification.addAction(action);
    }
    if (suppressable) {
      notification.addAction(new NotificationAction(IdeBundle.message("sys.health.acknowledge.action")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          notification.expire();
          PropertiesComponent.getInstance().setValue("ignore." + key, "true");
        }
      });
    }
    notification.setImportant(true);

    Notifications.Bus.notify(notification);
  }

  private static final class MyNotification extends Notification implements NotificationFullContent {
    MyNotification(@NotNull @NlsContexts.NotificationContent String content) {
      super(DISPLAY_ID, "", content, NotificationType.WARNING);
    }
  }

  private static void startDiskSpaceMonitoring() {
    if (SystemProperties.getBooleanProperty("idea.no.system.path.space.monitoring", false)) {
      return;
    }

    final File file = new File(PathManager.getSystemPath());
    final AtomicBoolean reported = new AtomicBoolean();
    final ThreadLocal<Future<Long>> ourFreeSpaceCalculation = new ThreadLocal<>();

    AppExecutorUtil.getAppScheduledExecutorService().schedule(new Runnable() {
      private static final long LOW_DISK_SPACE_THRESHOLD = 50 * 1024 * 1024;
      private static final long MAX_WRITE_SPEED_IN_BPS = 500 * 1024 * 1024;  // 500 MB/sec is near max SSD sequential write speed

      @Override
      public void run() {
        if (!reported.get()) {
          Future<@Nullable Long> future = ourFreeSpaceCalculation.get();
          if (future == null) {
            ourFreeSpaceCalculation.set(future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
              // file.getUsableSpace() can fail and return 0 (e.g. after macOS restart or awakening from sleep)
              // so several times try to recalculate usable space on receiving 0 to be sure
              long fileUsableSpace = file.getUsableSpace();
              while (fileUsableSpace == 0) {
                TimeoutUtil.sleep(5000);  // hopefully we are not hammering the disk too much
                fileUsableSpace = file.getUsableSpace();
              }
              return fileUsableSpace;
            }));
          }
          if (!future.isDone() || future.isCancelled()) {
            restart(1);
            return;
          }

          try {
            Long result = future.get();
            if (result == null) return;
            ourFreeSpaceCalculation.set(null);

            long usableSpace = result;
            long timeout = MathUtil.clamp((usableSpace - LOW_DISK_SPACE_THRESHOLD) / MAX_WRITE_SPEED_IN_BPS, 5, 3600);
            if (usableSpace < LOW_DISK_SPACE_THRESHOLD) {
              if (ReadAction.compute(() -> NotificationsConfiguration.getNotificationsConfiguration()) == null) {
                ourFreeSpaceCalculation.set(future);
                restart(1);
                return;
              }
              reported.compareAndSet(false, true);

              SwingUtilities.invokeLater(() -> {
                String productName = ApplicationNamesInfo.getInstance().getFullProductName();
                String message = IdeBundle.message("low.disk.space.message", productName);
                if (usableSpace < 100 * 1024) {
                  LOG.warn(message + " (" + usableSpace + ")");
                  Messages.showErrorDialog(message, IdeBundle.message("dialog.title.fatal.configuration.problem"));
                  reported.compareAndSet(true, false);
                  restart(timeout);
                }
                else {
                  NotificationGroupManager.getInstance().getNotificationGroup(DISPLAY_ID)
                    .createNotification(message, file.getPath(), NotificationType.ERROR, null)
                    .whenExpired(() -> {
                      reported.compareAndSet(true, false);
                      restart(timeout);
                    })
                    .notify(null);
                }
              });
            }
            else {
              restart(timeout);
            }
          }
          catch (Exception ex) {
            LOG.error(ex);
          }
        }
      }

      private void restart(long timeout) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(this, timeout, TimeUnit.SECONDS);
      }
    }, 1, TimeUnit.SECONDS);
  }

  private interface LibC extends Library {
    Pointer SIG_IGN = new Pointer(1L);

    interface Handler extends Callback {
      void callback(int sig);

      Handler TERMINATE = sig -> System.exit(128 + sig);  // ref: java.lang.Terminator
    }

    int sigaction(int sig, Pointer action, Pointer oldAction);
    Pointer signal(int sig, Handler handler);
  }
}
