// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.VMOptions;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.actions.EditCustomVmOptionsAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.jna.JnaLoader;
import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationFullContent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.JdkBundle;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.lang.JavaVersion;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.io.File;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemHealthMonitor implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(SystemHealthMonitor.class);

  private static final NotificationGroup GROUP = new NotificationGroup("System Health", NotificationDisplayType.STICKY_BALLOON, true);
  private static final String SWITCH_JDK_ACTION = "SwitchBootJdk";
  private static final JavaVersion MIN_RECOMMENDED_JDK = JavaVersion.compose(8, 0, 144, 0, false);

  private final PropertiesComponent myProperties;

  public SystemHealthMonitor(@NotNull PropertiesComponent properties) {
    myProperties = properties;
  }

  @Override
  public void initComponent() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      checkRuntime();
      checkReservedCodeCacheSize();
      checkIBus();
      checkSignalBlocking();
      startDiskSpaceMonitoring();
    });
  }

  private void checkRuntime() {
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

        NotificationAction switchAction = new NotificationAction("Switch") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
            notification.expire();
            ActionManager.getInstance().getAction(SWITCH_JDK_ACTION).actionPerformed(null);
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

  private void checkReservedCodeCacheSize() {
    int minReservedCodeCacheSize = 240;
    int reservedCodeCacheSize = VMOptions.readOption(VMOptions.MemoryKind.CODE_CACHE, true);
    if (reservedCodeCacheSize > 0 && reservedCodeCacheSize < minReservedCodeCacheSize) {
      EditCustomVmOptionsAction vmEditAction = new EditCustomVmOptionsAction();
      NotificationAction action = new NotificationAction(IdeBundle.message("vmoptions.edit.action")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          notification.expire();
          ActionUtil.performActionDumbAware(vmEditAction, e);
        }
      };
      showNotification("vmoptions.warn.message", vmEditAction.isEnabled() ? action : null, reservedCodeCacheSize, minReservedCodeCacheSize);
    }
  }

  private void checkIBus() {
    if (SystemInfo.isXWindow) {
      String xim = System.getenv("XMODIFIERS");
      if (xim != null && xim.contains("im=ibus")) {
        String version = ExecUtil.execAndReadLine(new GeneralCommandLine("ibus-daemon", "--version"));
        if (version != null) {
          Matcher m = Pattern.compile("ibus-daemon - Version ([0-9.]+)").matcher(version);
          if (m.find() && StringUtil.compareVersionNumbers(m.group(1), "1.5.11") < 0) {
            String fix = System.getenv("IBUS_ENABLE_SYNC_MODE");
            if (fix == null || fix.isEmpty() || fix.equals("0") || fix.equalsIgnoreCase("false")) {
              showNotification("ibus.blocking.warn.message", detailsAction("https://youtrack.jetbrains.com/issue/IDEA-78860"));
            }
          }
        }
      }
    }
  }

  private void checkSignalBlocking() {
    if (SystemInfo.isUnix && JnaLoader.isLoaded()) {
      try {
        LibC lib = Native.loadLibrary("c", LibC.class);
        Memory buf = new Memory(1024);
        if (lib.sigaction(LibC.SIGINT, null, buf) == 0) {
          long handler = Native.POINTER_SIZE == 8 ? buf.getLong(0) : buf.getInt(0);
          if (handler == LibC.SIG_IGN) {
            showNotification("ide.sigint.ignored.message", detailsAction("https://youtrack.jetbrains.com/issue/IDEA-157989"));
          }
        }
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    }
  }

  private void showNotification(@PropertyKey(resourceBundle = "messages.IdeBundle") String key,
                                @Nullable NotificationAction action,
                                Object... params) {
    boolean ignored = myProperties.isValueSet("ignore." + key);
    LOG.info("issue detected: " + key + (ignored ? " (ignored)" : ""));
    if (ignored) return;

    Notification notification = new MyNotification(IdeBundle.message(key, params));
    if (action != null) {
      notification.addAction(action);
    }
    notification.addAction(new NotificationAction(IdeBundle.message("sys.health.acknowledge.action")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        myProperties.setValue("ignore." + key, "true");
      }
    });
    notification.setImportant(true);

    ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(notification));
  }

  private static final class MyNotification extends Notification implements NotificationFullContent {
    public MyNotification(@NotNull String content) {
      super(GROUP.getDisplayId(), "", content, NotificationType.WARNING);
    }
  }

  private static NotificationAction detailsAction(String url) {
    return new BrowseNotificationAction(IdeBundle.message("sys.health.details"), url);
  }

  private static void startDiskSpaceMonitoring() {
    if (SystemProperties.getBooleanProperty("idea.no.system.path.space.monitoring", false)) {
      return;
    }

    final File file = new File(PathManager.getSystemPath());
    final AtomicBoolean reported = new AtomicBoolean();
    final ThreadLocal<Future<Long>> ourFreeSpaceCalculation = new ThreadLocal<>();

    JobScheduler.getScheduler().schedule(new Runnable() {
      private static final long LOW_DISK_SPACE_THRESHOLD = 50 * 1024 * 1024;
      private static final long MAX_WRITE_SPEED_IN_BPS = 500 * 1024 * 1024;  // 500 MB/sec is near max SSD sequential write speed

      @Override
      public void run() {
        if (!reported.get()) {
          Future<Long> future = ourFreeSpaceCalculation.get();
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
            final long fileUsableSpace = future.get();
            final long timeout = Math.min(3600, Math.max(5, (fileUsableSpace - LOW_DISK_SPACE_THRESHOLD) / MAX_WRITE_SPEED_IN_BPS));
            ourFreeSpaceCalculation.set(null);

            if (fileUsableSpace < LOW_DISK_SPACE_THRESHOLD) {
              if (ReadAction.compute(() -> NotificationsConfiguration.getNotificationsConfiguration()) == null) {
                ourFreeSpaceCalculation.set(future);
                restart(1);
                return;
              }
              reported.compareAndSet(false, true);

              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(() -> {
                String productName = ApplicationNamesInfo.getInstance().getFullProductName();
                String message = IdeBundle.message("low.disk.space.message", productName);
                if (fileUsableSpace < 100 * 1024) {
                  LOG.warn(message + " (" + fileUsableSpace + ")");
                  Messages.showErrorDialog(message, "Fatal Configuration Problem");
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
        JobScheduler.getScheduler().schedule(this, timeout, TimeUnit.SECONDS);
      }
    }, 1, TimeUnit.SECONDS);
  }

  @SuppressWarnings({"SpellCheckingInspection", "SameParameterValue"})
  private interface LibC extends Library {
    int SIGINT = 2;
    long SIG_IGN = 1L;
    int sigaction(int signum, Pointer act, Pointer oldact);
  }
}