// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.BrowserLauncherAppless;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.containers.ContainerUtilRt.newArrayList;

public class BrowserUtil {
  // The pattern for 'scheme' mainly according to RFC1738.
  // We have to violate the RFC since we need to distinguish
  // real schemes from local Windows paths; The only difference
  // with RFC is that we do not allow schemes with length=1 (in other case
  // local paths like "C:/temp/index.html" would be erroneously interpreted as
  // external URLs.)
  private static final Pattern ourExternalPrefix = Pattern.compile("^[\\w+.\\-]{2,}:");
  private static final Pattern ourAnchorSuffix = Pattern.compile("#(.*)$");

  private BrowserUtil() { }

  public static boolean isAbsoluteURL(String url) {
    return ourExternalPrefix.matcher(url.toLowerCase(Locale.ENGLISH)).find();
  }

  public static String getDocURL(String url) {
    Matcher anchorMatcher = ourAnchorSuffix.matcher(url);
    return anchorMatcher.find() ? anchorMatcher.reset().replaceAll("") : url;
  }

  @Nullable
  public static URL getURL(String url) throws MalformedURLException {
    return isAbsoluteURL(url) ? VfsUtilCore.convertToURL(url) : new URL("file", "", url);
  }

  public static void browse(@NotNull VirtualFile file) {
    browse(file.getUrl());
  }

  public static void browse(@NotNull File file) {
    getBrowserLauncher().browse(file);
  }

  public static void browse(@NotNull URL url) {
    browse(url.toExternalForm());
  }

  /**
   * @deprecated Use {@link #browse(String)}
   */
  @Deprecated
  public static void launchBrowser(@NotNull String url) {
    browse(url);
  }

  public static void browse(@NotNull @NonNls String url) {
    getBrowserLauncher().browse(url, null);
  }

  private static BrowserLauncher getBrowserLauncher() {
    BrowserLauncher launcher = ApplicationManager.getApplication() == null ? null : BrowserLauncher.getInstance();
    return launcher == null ? new BrowserLauncherAppless() : launcher;
  }

  public static void open(@NotNull String url) {
    getBrowserLauncher().open(url);
  }

  /**
   * tries to launch a browser using every possible way
   */
  public static void browse(@NotNull URI uri) {
    getBrowserLauncher().browse(uri);
  }

  public static void browse(@NotNull String url, @Nullable Project project) {
    getBrowserLauncher().browse(url, null, project);
  }

  @NotNull
  public static List<String> getOpenBrowserCommand(@NonNls @NotNull String browserPathOrName, boolean newWindowIfPossible) {
    if (new File(browserPathOrName).isFile()) {
      return Collections.singletonList(browserPathOrName);
    }
    else if (SystemInfo.isMac) {
      List<String> command = newArrayList(ExecUtil.getOpenCommandPath(), "-a", browserPathOrName);
      if (newWindowIfPossible) {
        command.add("-n");
      }
      return command;
    }
    else if (SystemInfo.isWindows) {
      return Arrays.asList(ExecUtil.getWindowsShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(""), browserPathOrName);
    }
    else {
      return Collections.singletonList(browserPathOrName);
    }
  }

  public static boolean isOpenCommandSupportArgs() {
    return SystemInfo.isMacOSSnowLeopard;
  }

  @NotNull
  public static String getDefaultAlternativeBrowserPath() {
    if (SystemInfo.isWindows) {
      return "C:\\Program Files\\Internet Explorer\\IExplore.exe";
    }
    else if (SystemInfo.isMac) {
      return "open";
    }
    else if (SystemInfo.isUnix) {
      return "/usr/bin/firefox";
    }
    else {
      return "";
    }
  }
}