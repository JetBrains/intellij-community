/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.VMOptions;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Bitness;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.JdkBundle;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SystemHealthMonitor implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(SystemHealthMonitor.class);

  private static final NotificationGroup GROUP = new NotificationGroup("System Health", NotificationDisplayType.STICKY_BALLOON, false);
  private static final NotificationGroup LOG_GROUP = NotificationGroup.logOnlyGroup("System Health (minor)");
  private static final String SWITCH_JDK_ACTION = "SwitchBootJdk";
  private static final String LATEST_JDK_RELEASE = "1.8.0u112";

  private final PropertiesComponent myProperties;

  public SystemHealthMonitor(@NotNull PropertiesComponent properties) {
    myProperties = properties;
  }

  @Override
  public void initComponent() {
    checkRuntime();
    checkReservedCodeCacheSize();
    checkIBus();
    checkSignalBlocking();
    checkLauncherScript();
    checkHiDPIMode();
    startDiskSpaceMonitoring();
  }

  private void checkReservedCodeCacheSize() {
    int minReservedCodeCacheSize = 240;
    int reservedCodeCacheSize = VMOptions.readOption(VMOptions.MemoryKind.CODE_CACHE, true);
    if (reservedCodeCacheSize > 0 && reservedCodeCacheSize < minReservedCodeCacheSize) {
      showNotification(new KeyHyperlinkAdapter("vmoptions.warn.message"), reservedCodeCacheSize, minReservedCodeCacheSize);
    }
  }

  private void checkRuntime() {
    if (StringUtil.endsWithIgnoreCase(System.getProperty("java.version", ""), "-ea")) {
      showNotification(new KeyHyperlinkAdapter("unsupported.jvm.ea.message"));
    }

    JdkBundle bundle = JdkBundle.createBoot();
    if (bundle != null && !bundle.isBundled()) {
      Version version = bundle.getVersion();
      Integer updateNumber = bundle.getUpdateNumber();
      if (version != null && updateNumber != null && version.major == 1 && version.minor == 8 && updateNumber < 112) {
        final String bundleVersion = version.toCompactString() + "u" + updateNumber;
        boolean showSwitchOption = false;

        final File bundledJDKAbsoluteLocation = JdkBundle.getBundledJDKAbsoluteLocation();
        if (bundledJDKAbsoluteLocation.exists() && bundle.getBitness() == Bitness.x64) {
          if (SystemInfo.isMacIntel64) {
            showSwitchOption = true;
          }
          else if (SystemInfo.isWindows || SystemInfo.isLinux) {
            JdkBundle bundledJdk = JdkBundle.createBundle(bundledJDKAbsoluteLocation, false, false);
            if (bundledJdk != null && bundledJdk.getVersion() != null) {
              showSwitchOption = true; // Version of bundled jdk is available, so the jdk is compatible with underlying OS
            }
          }
        }

        showNotification(new KeyHyperlinkAdapter(showSwitchOption ? "outdated.jvm.version.message1" : "outdated.jvm.version.message2") {
          @Override
          protected void hyperlinkActivated(HyperlinkEvent e) {
            if ("switch".equals(e.getDescription())) {
              ActionManager.getInstance().getAction(SWITCH_JDK_ACTION).actionPerformed(null);
            }
            else {
              super.hyperlinkActivated(e);
            }
          }
        }, bundleVersion, LATEST_JDK_RELEASE);
      }
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
              showNotification(new KeyHyperlinkAdapter("ibus.blocking.warn.message"));
            }
          }
        }
      }
    }
  }

  private void checkSignalBlocking() {
    if (SystemInfo.isUnix) {
      try {
        LibC lib = (LibC)Native.loadLibrary("c", LibC.class);
        Memory buf = new Memory(1024);
        if (lib.sigaction(LibC.SIGINT, null, buf) == 0) {
          long handler = Native.POINTER_SIZE == 8 ? buf.getLong(0) : buf.getInt(0);
          if (handler == LibC.SIG_IGN) {
            showNotification(new KeyHyperlinkAdapter("ide.sigint.ignored.message"));
          }
        }
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    }
  }

  private void checkLauncherScript() {
    if (SystemInfo.isXWindow && System.getProperty("jb.restart.code") != null) {
      showNotification(new KeyHyperlinkAdapter("ide.launcher.script.outdated"));
    }
  }

  private void checkHiDPIMode() {
    // if switched from JRE-HiDPI to IDE-HiDPI
    boolean switchedHiDPIMode = SystemInfo.isJetBrainsJvm && "true".equalsIgnoreCase(System.getProperty("sun.java2d.uiScale.enabled")) && !UIUtil.isJreHiDPIEnabled();
    if (SystemInfo.isWindows && (switchedHiDPIMode || RemoteDesktopService.isRemoteSession())) {
      showNotification(new KeyHyperlinkAdapter("ide.set.hidpi.mode"));
    }
  }

  private void showNotification(KeyHyperlinkAdapter adapter, Object... params) {
    @PropertyKey(resourceBundle = "messages.IdeBundle") String key = adapter.key;
    boolean ignored = adapter.isIgnored();
    LOG.info("issue detected: " + key + (ignored ? " (ignored)" : ""));
    if (ignored) return;

    String message = IdeBundle.message(key, params) + IdeBundle.message("sys.health.acknowledge.link");

    Application app = ApplicationManager.getApplication();
    app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appFrameCreated(String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject) {
        app.invokeLater(() -> {
          JComponent component = WindowManager.getInstance().findVisibleFrame().getRootPane();
          if (component != null) {
            Rectangle rect = component.getVisibleRect();
            JBPopupFactory.getInstance()
              .createHtmlTextBalloonBuilder(message, MessageType.WARNING, adapter)
              .setFadeoutTime(-1)
              .setHideOnFrameResize(false)
              .setHideOnLinkClick(true)
              .setDisposable(app)
              .createBalloon()
              .show(new RelativePoint(component, new Point(rect.x + 30, rect.y + rect.height - 10)), Balloon.Position.above);
          }

          Notification notification = LOG_GROUP.createNotification("", message, NotificationType.WARNING,
            new NotificationListener.Adapter() {
              @Override
              protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
                adapter.hyperlinkActivated(e);
              }
            });
          notification.setImportant(true);
          Notifications.Bus.notify(notification);
        });
      }
    });
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

  private class KeyHyperlinkAdapter extends HyperlinkAdapter {
    private final String key;

    private KeyHyperlinkAdapter(@PropertyKey(resourceBundle = "messages.IdeBundle") String key) {
      this.key = key;
    }

    private boolean isIgnored() {
      return myProperties.isValueSet("ignore." + key);
    }

    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      String url = e.getDescription();
      if ("ack".equals(url)) {
        myProperties.setValue("ignore." + key, "true");
      }
      else {
        BrowserUtil.browse(url);
      }
    }
  }
}