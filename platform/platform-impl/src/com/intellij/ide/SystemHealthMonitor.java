// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.diagnostic.VMOptions;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.ide.actions.EditCustomVmOptionsAction;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.StartupUtil;
import com.intellij.jna.JnaLoader;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.impl.NotificationFullContent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.MathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.system.CpuArch;
import com.intellij.util.ui.IoErrorText;
import com.sun.jna.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class SystemHealthMonitor extends PreloadingActivity {
  private static final Logger LOG = Logger.getInstance(SystemHealthMonitor.class);
  private static final String NOTIFICATION_GROUP_ID = "System Health";

  @Override
  public void preload(@NotNull ProgressIndicator indicator) {
    checkInstallationIntegrity();
    checkIdeDirectories();
    checkRuntime();
    checkReservedCodeCacheSize();
    checkEnvironment();
    checkSignalBlocking();
    checkTempDirEnvVars();
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
        showNotification("implicit.plugin.directory.path", true, null, shorten(PathManager.getPluginsPath()));
      }
      if (System.getProperty(PathManager.PROPERTY_SYSTEM_PATH) != null && System.getProperty(PathManager.PROPERTY_LOG_PATH) == null) {
        showNotification("implicit.log.directory.path", true, null, shorten(PathManager.getLogPath()));
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
    if (!CpuArch.isEmulated()) return;
    LOG.info(CpuArch.CURRENT + " appears to be emulated");

    if (SystemInfo.isMac && CpuArch.isIntel64()) {
      NotificationAction downloadAction = NotificationAction.createSimpleExpiring(
        IdeBundle.message("bundled.jre.m1.arch.message.download"),
        () -> BrowserUtil.browse("https://www.jetbrains.com/products/#type=ide"));
      showNotification("bundled.jre.m1.arch.message", true, downloadAction, ApplicationNamesInfo.getInstance().getFullProductName());
    }

    String jreHome = SystemProperties.getJavaHome();
    if (!(PathManager.isUnderHomeDirectory(jreHome) || isModernJBR())) {
      // boot JRE is non-bundled and is either non-JB or older than bundled
      NotificationAction switchAction = null;

      String directory = PathManager.getCustomOptionsDirectory();
      if (directory != null && (SystemInfo.isWindows || SystemInfo.isMac || SystemInfo.isLinux) && isJbrOperational()) {
        String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
        String configName = scriptName + (!SystemInfo.isWindows ? "" : CpuArch.isIntel64() ? "64.exe" : ".exe") + ".jdk";
        Path configFile = Path.of(directory, configName);
        if (Files.isRegularFile(configFile)) {
          switchAction = NotificationAction.createSimpleExpiring(IdeBundle.message("action.SwitchToJBR.text"), () -> {
            try {
              Files.delete(configFile);
              ApplicationManagerEx.getApplicationEx().restart(true);
            }
            catch (IOException x) {
              LOG.warn("cannot delete " + configFile, x);
              String content = IdeBundle.message("cannot.delete.jre.config", configFile, IoErrorText.message(x));
              new Notification(NOTIFICATION_GROUP_ID, content, NotificationType.ERROR).notify(null);
            }
          });
        }
      }

      jreHome = StringUtil.trimEnd(jreHome, "/Contents/Home");
      showNotification("bundled.jre.version.message", false, switchAction, JavaVersion.current(), System.getProperty("java.vendor"), jreHome);
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
    int minReservedCodeCacheSize = PluginManagerCore.isRunningFromSources() ? 240 : 512;
    if (reservedCodeCacheSize > 0 && reservedCodeCacheSize < minReservedCodeCacheSize) {
      EditCustomVmOptionsAction vmEditAction = new EditCustomVmOptionsAction();
      NotificationAction action = vmEditAction.isEnabled() ? NotificationAction.createExpiring(
        IdeBundle.message("vm.options.edit.action.cap"), (e, n) -> vmEditAction.actionPerformed(e)) : null;
      showNotification("code.cache.warn.message", true, action, reservedCodeCacheSize, minReservedCodeCacheSize);
    }
  }

  private static void checkEnvironment() {
    List<String> usedVars = Stream.of("_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS")
      .filter(var -> Strings.isNotEmpty(System.getenv(var)))
      .collect(Collectors.toList());
    if (!usedVars.isEmpty()) {
      showNotification("vm.options.env.vars", true, null, String.join(", ", usedVars));
    }

    AppExecutorUtil.getAppExecutorService().execute(() -> {
      try {
        if (StartupUtil.getShellEnvLoadingFuture().get() == Boolean.FALSE) {
          NotificationAction action = NotificationAction.createSimpleExpiring(
            IdeBundle.message("shell.env.loading.learn.more"), () -> BrowserUtil.browse("https://jb.gg/shell-env"));
          String appName = ApplicationNamesInfo.getInstance().getFullProductName(), shell = System.getenv("SHELL");
          showNotification("shell.env.loading.failed", true, action, appName, shell);
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }

  private static void checkSignalBlocking() {
    if (SystemInfo.isUnix && JnaLoader.isLoaded()) {
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

  private static void checkTempDirEnvVars() {
    List<String> envVars = SystemInfo.isWindows ? List.of("TMP", "TEMP") : List.of("TMPDIR");
    for (String name : envVars) {
      String value = System.getenv(name);
      if (value != null) {
        try {
          if (!Files.isDirectory(Path.of(value))) {
            showNotification("temp.dir.env.invalid", false, null, name, value);
          }
        }
        catch (Exception e) {
          LOG.warn(e);
          showNotification("temp.dir.env.invalid", false, null, name, value);
        }
      }
    }
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

    Notification notification = new MyNotification(IdeBundle.message(key, params), NotificationType.WARNING, key);
    if (action != null) {
      notification.addAction(action);
    }
    if (suppressable) {
      notification.addAction(NotificationAction.createSimpleExpiring(
        IdeBundle.message("sys.health.acknowledge.action"), () -> PropertiesComponent.getInstance().setValue("ignore." + key, "true")));
    }
    notification.setImportant(true);
    notification.setSuggestionType(true);

    Notifications.Bus.notify(notification);
  }

  private static void startDiskSpaceMonitoring() {
    if (SystemProperties.getBooleanProperty("idea.no.system.path.space.monitoring", false)) {
      return;
    }

    FileStore store;
    try {
      store = Files.getFileStore(Path.of(PathManager.getSystemPath()));
    }
    catch (IOException e) {
      LOG.error(e);
      return;
    }

    AppExecutorUtil.getAppScheduledExecutorService().schedule(new Runnable() {
      private static final long NO_DISK_SPACE_THRESHOLD = 1 << 20;
      private static final long LOW_DISK_SPACE_THRESHOLD = 50 << 20;
      private static final long MAX_WRITE_SPEED_IN_BPS = 500 << 20;  // 500 MB/s is (somewhat outdated) peak SSD write speed

      private volatile @Nullable Future<Long> freeSpaceCalculator;

      @Override
      public void run() {
        Future<Long> future = freeSpaceCalculator;
        if (future == null) {
          freeSpaceCalculator = future = ProcessIOExecutorService.INSTANCE.submit(store::getUsableSpace);
        }
        if (!future.isDone()) {
          restart(1);
          return;  // calculating...
        }

        long usableSpace;
        try {
          usableSpace = future.get();
          freeSpaceCalculator = null;
        }
        catch (Exception e) {
          LOG.error(e);
          return;  // something went wrong, aborting
        }

        if (usableSpace < NO_DISK_SPACE_THRESHOLD) {
          LOG.warn("Extremely low disk space: " + usableSpace);
          SwingUtilities.invokeLater(() -> {
            Messages.showErrorDialog(IdeBundle.message("no.disk.space.message", store.name()), IdeBundle.message("no.disk.space.title"));
            restart(5);
          });
        }
        else if (usableSpace < LOW_DISK_SPACE_THRESHOLD) {
          LOG.warn("Low disk space: " + usableSpace);
          new MyNotification(IdeBundle.message("low.disk.space.message", store.name()), NotificationType.WARNING, "low.disk")
            .setTitle(IdeBundle.message("low.disk.space.title"))
            .whenExpired(() -> restart(5))
            .notify(null);
        }
        else {
          restart(MathUtil.clamp((usableSpace - LOW_DISK_SPACE_THRESHOLD) / MAX_WRITE_SPEED_IN_BPS, 5, 3600));
        }
      }

      private void restart(long delaySeconds) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(this, delaySeconds, TimeUnit.SECONDS);
      }
    }, 1, TimeUnit.SECONDS);
  }

  private static final class MyNotification extends Notification implements NotificationFullContent {
    private MyNotification(@NlsContexts.NotificationContent String content, NotificationType type, @Nullable String displayId) {
      super(NOTIFICATION_GROUP_ID, content, type);
      if (displayId != null) setDisplayId(displayId);
    }
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
