/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.BrowserLauncherAppless;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
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
  private static final Pattern ourExternalPrefix = Pattern.compile("^[\\w\\+\\.\\-]{2,}:");
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
    browse(VfsUtil.toUri(file));
  }

  public static void browse(@NotNull File file) {
    getBrowserLauncher().browse(file);
  }

  public static void browse(@NotNull URL url) {
    browse(url.toExternalForm());
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * @deprecated Use {@link #browse(String)}
   */
  public static void launchBrowser(@NotNull @NonNls String url) {
    browse(url);
  }

  public static void browse(@NotNull @NonNls String url) {
    getBrowserLauncher().browse(url, null);
  }

  private static BrowserLauncher getBrowserLauncher() {
    BrowserLauncher launcher = ApplicationManager.getApplication() == null ? null : BrowserLauncher.getInstance();
    return launcher == null ? new BrowserLauncherAppless() : launcher;
  }

  public static void open(@NotNull @NonNls String url) {
    getBrowserLauncher().open(url);
  }

  /**
   * Main method: tries to launch a browser using every possible way
   */
  public static void browse(@NotNull URI uri) {
    getBrowserLauncher().browse(uri);
  }

  public static void browse(@NotNull String url, @Nullable Project project) {
    getBrowserLauncher().browse(url, null, project);
  }

  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  @Deprecated
  public static List<String> getOpenBrowserCommand(@NonNls @NotNull String browserPathOrName) {
    return getOpenBrowserCommand(browserPathOrName, false);
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
