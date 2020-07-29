// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.diagnostic.VMOptions;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.ide.actions.EditCustomVmOptionsAction;
import com.intellij.ide.plugins.PluginManagerCore;
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.JdkBundle;
import com.intellij.util.MathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.lang.JavaVersion;
import com.sun.jna.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.io.File;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class SystemHealthMonitor extends PreloadingActivity {
  private static final Logger LOG = Logger.getInstance(SystemHealthMonitor.class);

  private static final NotificationGroup GROUP =
    new NotificationGroup("System Health", NotificationDisplayType.STICKY_BALLOON, true, null, null, null, PluginManagerCore.CORE_ID);
  private static final JavaVersion MIN_RECOMMENDED_JDK = JavaVersion.compose(8, 0, 144, 0, false);
  private static final int MIN_RESERVED_CODE_CACHE_SIZE = 240;

  @Override
  public void preload(@NotNull ProgressIndicator indicator) {
    checkIdeDirectories();
    checkRuntime();
    checkReservedCodeCacheSize();
    checkEnvironment();
    checkSignalBlocking();
    startDiskSpaceMonitoring();
  }

  private static void checkIdeDirectories() {
    if (System.getProperty(PathManager.PROPERTY_PATHS_SELECTOR) != null) {
      if (System.getProperty(PathManager.PROPERTY_CONFIG_PATH) != null && System.getProperty(PathManager.PROPERTY_PLUGINS_PATH) == null) {
        showNotification("implicit.plugin.directory.path", null);
      }
      if (System.getProperty(PathManager.PROPERTY_SYSTEM_PATH) != null && System.getProperty(PathManager.PROPERTY_LOG_PATH) == null) {
        showNotification("implicit.log.directory.path", null);
      }
    }
  }

  private static void checkRuntime() {
    if (JavaVersion.current().ea) {
      showNotification("unsupported.jvm.ea.message", null);
    }

    JdkBundle bootJdk = JdkBundle.createBoot();
    if (!bootJdk.isBundled()) {
      boolean outdatedRuntime = bootJdk.getBundleVersion().compareTo(MIN_RECOMMENDED_JDK) < 0;
      if (!SystemInfo.isJetBrainsJvm || outdatedRuntime) {
        JdkBundle bundledJdk;
        boolean validBundledJdk =
          (SystemInfo.isWindows || SystemInfo.isMac || SystemInfo.isLinux) &&
          (bundledJdk = JdkBundle.createBundled()) != null &&
          bundledJdk.isOperational();

        NotificationAction switchAction = new NotificationAction(IdeBundle.messagePointer("action.SwitchToJBR.text")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
            notification.expire();

            String appName = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getProductName());
            File config = new File(PathManager.getConfigPath(),
                                   appName + (SystemInfo.isWindows ? (SystemInfo.is64Bit ? "64.exe.jdk" : ".exe.jdk") : ".jdk"));

            if (!FileUtil.delete(config)) {
              LOG.warn("Can't delete JDK configuration file: " + config.getAbsolutePath());
            }
            ApplicationManager.getApplication().restart();
          }
        };

        String current = bootJdk.getBundleVersion().toString();
        if (!SystemInfo.isJetBrainsJvm) current += " by " + SystemInfo.JAVA_VENDOR;
        if (outdatedRuntime && validBundledJdk) {
          showNotification("outdated.jre.version.message1", switchAction, current, MIN_RECOMMENDED_JDK);
        }
        else if (outdatedRuntime) {
          showNotification("outdated.jre.version.message2", null, current, MIN_RECOMMENDED_JDK);
        }
        else if (validBundledJdk) {
          showNotification("bundled.jre.version.message", switchAction, current);
        }
      }
    }
  }

  private static void checkReservedCodeCacheSize() {
    int reservedCodeCacheSize = VMOptions.readOption(VMOptions.MemoryKind.CODE_CACHE, true);
    if (reservedCodeCacheSize > 0 && reservedCodeCacheSize < MIN_RESERVED_CODE_CACHE_SIZE) {
      EditCustomVmOptionsAction vmEditAction = new EditCustomVmOptionsAction();
      NotificationAction action = new NotificationAction(IdeBundle.message("vm.options.edit.action.cap")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          notification.expire();
          ActionUtil.performActionDumbAware(vmEditAction, e);
        }
      };
      showNotification("code.cache.warn.message", vmEditAction.isEnabled() ? action : null, reservedCodeCacheSize, MIN_RESERVED_CODE_CACHE_SIZE);
    }
  }

  private static void checkEnvironment() {
    String usedVars = Stream.of("_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS")
      .filter(var -> StringUtil.isNotEmpty(System.getenv(var)))
      .collect(Collectors.joining(", "));
    if (!usedVars.isEmpty()) {
      showNotification("vm.options.env.vars", null, usedVars);
    }
  }

  private static void checkSignalBlocking() {
    if (SystemInfo.isUnix & JnaLoader.isLoaded()) {
      try {
        Memory sa = new Memory(256);
        if (LibC.sigaction(UnixProcessManager.SIGINT, Pointer.NULL, sa) == 0 && LibC.SIG_IGN.equals(sa.getPointer(0))) {
          LibC.signal(UnixProcessManager.SIGINT, LibC.Handler.TERMINATE);
          LOG.info("restored ignored INT handler");
        }
        if (LibC.sigaction(UnixProcessManager.SIGPIPE, Pointer.NULL, sa) == 0 && LibC.SIG_IGN.equals(sa.getPointer(0))) {
          LibC.signal(UnixProcessManager.SIGPIPE, LibC.Handler.NO_OP);
          LOG.info("restored ignored PIPE handler");
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
    boolean ignored = PropertiesComponent.getInstance().isValueSet("ignore." + key);
    LOG.warn("issue detected: " + key + (ignored ? " (ignored)" : ""));
    if (ignored) return;

    Notification notification = new MyNotification(IdeBundle.message(key, params));
    if (action != null) {
      notification.addAction(action);
    }
    notification.addAction(new NotificationAction(IdeBundle.message("sys.health.acknowledge.action")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        PropertiesComponent.getInstance().setValue("ignore." + key, "true");
      }
    });
    notification.setImportant(true);

    Notifications.Bus.notify(notification);
  }

  private static final class MyNotification extends Notification implements NotificationFullContent {
    MyNotification(@NotNull String content) {
      super(GROUP.getDisplayId(), "", content, NotificationType.WARNING);
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
              // file.getUsableSpace() can fail and return 0 e.g. after MacOSX restart or awakening from sleep
              // so several times try to recalculate usable space on receiving 0 to be sure
              long fileUsableSpace = file.getUsableSpace();
              while (fileUsableSpace == 0) {
                TimeoutUtil.sleep(5000);  // hopefully we will not hummer disk too much
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
                  GROUP.createNotification(message, file.getPath(), NotificationType.ERROR, null).whenExpired(() -> {
                    reported.compareAndSet(true, false);
                    restart(timeout);
                  }).notify(null);
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

  private static final class LibC {
    static {
      Native.register(LibC.class, NativeLibrary.getInstance("c"));
    }

    static final Pointer SIG_IGN = new Pointer(1L);

    interface Handler extends Callback {
      void callback(int sig);

      Handler TERMINATE = sig -> System.exit(128 + sig);  // ref: java.lang.Terminator
      Handler NO_OP = sig -> { };  // no-op handler just unmasks a signal for child processes
    }

    static native int sigaction(int sig, Pointer action, Pointer oldAction);
    static native Pointer signal(int sig, Handler handler);
  }
}