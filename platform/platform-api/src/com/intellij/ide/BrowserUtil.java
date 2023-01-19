// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.diagnostic.LoadingState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.BrowserLauncherAppless;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BrowserUtil {
  // The pattern for 'scheme' mainly according to RFC1738.
  // We have to violate the RFC since we need to distinguish real schemes from local Windows paths;
  // the only difference with RFC is that we do not allow schemes with length=1
  // (otherwise local paths like "C:/temp/index.html" would be incorrectly interpreted as URLs).
  private static final Pattern ourExternalPrefix = Pattern.compile("^[\\w+.\\-]{2,}:");
  private static final Pattern ourAnchorSuffix = Pattern.compile("#(.*)$");

  private BrowserUtil() { }

  public static boolean isAbsoluteURL(String url) {
    return ourExternalPrefix.matcher(StringUtil.toLowerCase(url)).find();
  }

  public static String getDocURL(String url) {
    Matcher anchorMatcher = ourAnchorSuffix.matcher(url);
    return anchorMatcher.find() ? anchorMatcher.reset().replaceAll("") : url;
  }

  public static @Nullable URL getURL(String url) throws MalformedURLException {
    return isAbsoluteURL(url) ? VfsUtilCore.convertToURL(url) : new URL("file", "", url);
  }

  public static void open(@NotNull String url) {
    getBrowserLauncher().open(url);
  }

  public static void browse(@NotNull VirtualFile file) {
    browse(file.getUrl(), null);
  }

  public static void browse(@NotNull File file) {
    getBrowserLauncher().browse(file);
  }

  public static void browse(@NotNull Path file) {
    getBrowserLauncher().browse(file);
  }

  public static void browse(@NotNull URL url) {
    browse(url.toExternalForm(), null);
  }

  public static void browse(@NotNull URI uri) {
    getBrowserLauncher().browse(uri);
  }

  public static void browse(@NotNull String url) {
    browse(url, null);
  }

  public static void browse(@NotNull String url, @Nullable Project project) {
    getBrowserLauncher().browse(url, null, project);
  }

  private static BrowserLauncher getBrowserLauncher() {
    return LoadingState.COMPONENTS_LOADED.isOccurred() ? BrowserLauncher.getInstance() : new BrowserLauncherAppless();
  }

  public static @NotNull List<String> getOpenBrowserCommand(@NotNull String browserPathOrName,
                                                            @Nullable String url,
                                                            @NotNull List<String> parameters,
                                                            boolean newWindowIfPossible) {
    var command = new ArrayList<String>();

    var path = NioFiles.toPath(browserPathOrName);
    if (path == null || !Files.isRegularFile(path)) {
      if (SystemInfo.isMac) {
        command.addAll(List.of(ExecUtil.getOpenCommandPath(), "-a", browserPathOrName));
        if (newWindowIfPossible) {
          command.add("-n");
        }
        if (url != null) {
          command.add(url);
        }
        if (!parameters.isEmpty()) {
          command.add("--args");
          command.addAll(parameters);
        }
      }
      else if (SystemInfo.isWindows) {
        command.addAll(List.of(ExecUtil.getWindowsShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(""), browserPathOrName));
        command.addAll(parameters);
        if (url != null) {
          command.add(url);
        }
      }
    }

    if (command.isEmpty()) {
      command.add(browserPathOrName);
      command.addAll(parameters);
      if (url != null) {
        command.add(url);
      }
    }

    return command;
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated the result is a pain to deal with; please use {@link #getOpenBrowserCommand(String, String, List, boolean)} instead */
  @Deprecated(forRemoval = true)
  public static @NotNull List<String> getOpenBrowserCommand(@NotNull String browserPathOrName, boolean newWindowIfPossible) {
    return getOpenBrowserCommand(browserPathOrName, null, List.of(), newWindowIfPossible);
  }

  /** @deprecated Use {@link #browse(String)} */
  @Deprecated(forRemoval = true)
  public static void launchBrowser(@NotNull String url) {
    browse(url);
  }
  //</editor-fold>
}
