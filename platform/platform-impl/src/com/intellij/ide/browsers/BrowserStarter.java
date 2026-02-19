// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.google.common.net.HostAndPort;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Urls;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class BrowserStarter {
  private static final Logger LOG = Logger.getInstance(BrowserStarter.class);

  private final StartBrowserSettings mySettings;
  private final RunConfiguration myRunConfiguration;
  private final BooleanSupplier myOutdated;
  private int myMaximumAttempt = 100;
  private boolean myOpenOnMaximumAttempt = false;

  public BrowserStarter(@NotNull RunConfiguration runConfiguration,
                        @NotNull StartBrowserSettings settings,
                        @NotNull BooleanSupplier outdated) {
    mySettings = settings;
    myRunConfiguration = runConfiguration;
    myOutdated = outdated;
  }

  public BrowserStarter(@NotNull RunConfiguration runConfiguration,
                        @NotNull StartBrowserSettings settings,
                        @NotNull ProcessHandler serverProcessHandler) {
    this(runConfiguration, settings, () -> serverProcessHandler.isProcessTerminating() || serverProcessHandler.isProcessTerminated());
  }

  public BrowserStarter setMaximumAttempts(int value) {
    myMaximumAttempt = value;
    return this;
  }

  public BrowserStarter setOpenOnMaximumAttempt(boolean value) {
    myOpenOnMaximumAttempt = value;
    return this;
  }

  public void start() {
    if (!mySettings.isSelected() || mySettings.getUrl() == null) {
      return;
    }

    HostAndPort hostAndPort = getHostAndPort(mySettings.getUrl());
    if (hostAndPort != null) {
      checkAndOpenPageLater(hostAndPort, 1, 300);
    }
    else {
      // we can't check page availability gracefully, so we just open it after some delay
      openPageLater();
    }
  }

  private static @Nullable HostAndPort getHostAndPort(@NotNull String rawUrl) {
    URI url = Urls.parseAsJavaUriWithoutParameters(rawUrl);
    if (url == null) {
      return null;
    }

    int port = url.getPort();
    if (port == -1) {
      port = "https".equals(url.getScheme()) ? 443 : 80;
    }
    return HostAndPort.fromParts(StringUtil.notNullize(url.getHost(), "127.0.0.1"), port);
  }

  private void checkAndOpenPageLater(@NotNull HostAndPort hostAndPort, int attemptNumber, int delayMillis) {
    JobScheduler.getScheduler().schedule(() -> checkAndOpenPage(hostAndPort, attemptNumber), delayMillis, TimeUnit.MILLISECONDS);
  }

  private void checkAndOpenPage(@NotNull HostAndPort hostAndPort, int attemptNumber) {
    if (isOutdated()) {
      LOG.info("Opening " + hostAndPort + " aborted");
    }
    else if (canConnect(hostAndPort.getHost(), hostAndPort.getPort())) {
      openPageNow();
    }
    else if (attemptNumber < myMaximumAttempt) {
      int delayMillis = getDelayMillis(attemptNumber);
      LOG.info("#" + attemptNumber + " check " + hostAndPort + " failed, scheduling next check in " + delayMillis + "ms");
      checkAndOpenPageLater(hostAndPort, attemptNumber + 1, delayMillis);
    }
    else if (attemptNumber == myMaximumAttempt && myOpenOnMaximumAttempt){
      LOG.info("#" + attemptNumber + " maximum attempt is reached, page opening is forced");
      openPageNow();
    }
    else {
      LOG.info("#" + attemptNumber + " check " + hostAndPort + " failed. Too many failed checks. Failed to open " + hostAndPort);
      showBrowserOpenTimeoutNotification();
    }
  }

  private static boolean canConnect(String host, int port) {
    if (NetUtils.canConnectToRemoteSocket(host, port)) {
      return true;
    }
    else if ("localhost".equals(host)) {
      // `new Socket("localhost", port)` cannot connect to IPv6-only socket
      // without -Djava.net.preferIPv6Addresses=true
      // => try IPv6 localhost explicitly
      return NetUtils.canConnectToRemoteSocket("::1", port);
    }
    else {
      return false;
    }
  }

  private static int getDelayMillis(int attemptNumber) {
    // [0 - 5 seconds] check each 500 ms
    if (attemptNumber < 10) {
      return 500;
    }
    // [5 - 25 seconds] check each 1000 ms
    if (attemptNumber < 20) {
      return 1000;
    }
    // [25 - 425 seconds] check each 5000 ms
    return 5000;
  }

  private void openPageLater() {
    JobScheduler.getScheduler().schedule(() -> openPageNow(), 1000, TimeUnit.MILLISECONDS);
  }

  protected void openPageNow() {
    if (!isOutdated()) {
      JavaScriptDebuggerStarter.Util.startDebugOrLaunchBrowser(myRunConfiguration, mySettings);
    }
  }

  protected boolean isOutdated() {
    return myOutdated.getAsBoolean();
  }

  private void showBrowserOpenTimeoutNotification() {
    NotificationGroup group =
      NotificationGroup.balloonGroup("URL does not respond notification", IdeBundle.message("browser.notification.timeout.group"));

    String url = Objects.requireNonNull(mySettings.getUrl());
    String openUrlDescription = "open_url";
    String content = IdeBundle.message("browser.notification.timeout.text", openUrlDescription, url);

    group.createNotification(IdeBundle.message("browser.notification.timeout.title"), content, NotificationType.ERROR)
      .setListener((notification, event) -> {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals(openUrlDescription)) {
          BrowserUtil.open(url);
          notification.expire();
        }
      })
      .notify(myRunConfiguration.getProject());
  }
}
