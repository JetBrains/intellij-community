// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class WebBrowser {
  public abstract @NotNull @NlsSafe String getName();

  public abstract @NotNull UUID getId();

  /**
   * @return the browser family, or {@code null} if the user did not specify it.
   * A browser without a family can open a URL, but the features that need a known family are not available for it.
   */
  public abstract @Nullable BrowserFamily getFamily();

  public abstract @NotNull Icon getIcon();

  public abstract @Nullable @NlsSafe String getPath();

  public abstract @NotNull @NlsContexts.NotificationContent String getBrowserNotFoundMessage();

  public abstract @Nullable BrowserSpecificSettings getSpecificSettings();

  public void addOpenUrlParameter(@NotNull List<? super String> command, @NotNull String url) {
    command.add(url);
  }

  /**
   * Opens {@code url} in this browser.
   * <p>
   * The launcher calls it after its common steps, such as the trust check and the URL signing.
   * To open a link, call {@link BrowserLauncher#browse(String, WebBrowser, Project)}, which runs these steps first.
   * <p>
   * By default, it runs the command of {@link #getPath()}, with the parameters and the environment of {@link #getSpecificSettings()}.
   * Without a path, it reports {@link #getBrowserNotFoundMessage()}.
   */
  @ApiStatus.Internal
  public void browse(@NotNull String url, @Nullable Project project) {
    String browserPath = PathUtil.toSystemDependentName(getPath());
    if (StringUtil.isEmptyOrSpaces(browserPath)) {
      BrowserLauncherAppless.showBrowserError(getBrowserNotFoundMessage(), url, this, project);
      return;
    }

    BrowserSpecificSettings settings = getSpecificSettings();
    List<String> parameters = settings == null ? List.of() : settings.getAdditionalParameters();
    Map<String, String> environment = settings == null ? Map.of() : settings.getEnvironmentVariables();
    GeneralCommandLine command = new GeneralCommandLine(BrowserUtil.getOpenBrowserCommand(browserPath, url, parameters, false))
      .withEnvironment(environment);
    BrowserLauncherAppless.runBrowserCommand(command, url, this, project);
  }
}
