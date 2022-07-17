// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.impl.local.NativeFileWatcherImpl;
import com.intellij.util.Restarter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WindowsDefenderChecker {
  private static final Logger LOG = Logger.getInstance(WindowsDefenderChecker.class);

  private static final Pattern WINDOWS_ENV_VAR_PATTERN = Pattern.compile("%([^%]+?)%");
  private static final Pattern WINDOWS_DEFENDER_WILDCARD_PATTERN = Pattern.compile("[?*]");
  private static final int WMIC_COMMAND_TIMEOUT_MS = 10000;
  private static final int POWERSHELL_COMMAND_TIMEOUT_MS = 10000;
  private static final int MAX_POWERSHELL_STDERR_LENGTH = 500;
  static final String IGNORE_VIRUS_CHECK = "ignore.virus.scanning.warn.message";

  public enum RealtimeScanningStatus {
    SCANNING_DISABLED,
    SCANNING_ENABLED,
    ERROR
  }

  public static WindowsDefenderChecker getInstance() {
    return ApplicationManager.getApplication().getService(WindowsDefenderChecker.class);
  }

  public static class CheckResult {
    public final RealtimeScanningStatus status;

    // Value in the map is true if the path is excluded, false otherwise
    public final Map<Path, Boolean> pathStatus;

    public CheckResult(RealtimeScanningStatus status, Map<Path, Boolean> pathStatus) {
      this.status = status;
      this.pathStatus = pathStatus;
    }
  }

  public boolean isVirusCheckIgnored(Project project) {
    return PropertiesComponent.getInstance().isTrueValue(IGNORE_VIRUS_CHECK) ||
           PropertiesComponent.getInstance(project).isTrueValue(IGNORE_VIRUS_CHECK);
  }

  public CheckResult checkWindowsDefender(@NotNull Project project) {
    final Boolean windowsDefenderActive = isWindowsDefenderActive();
    if (windowsDefenderActive == null || !windowsDefenderActive) {
      LOG.info("Windows Defender status: not used");
      return new CheckResult(RealtimeScanningStatus.SCANNING_DISABLED, Collections.emptyMap());
    }

    RealtimeScanningStatus scanningStatus = getRealtimeScanningEnabled();
    if (scanningStatus == RealtimeScanningStatus.SCANNING_ENABLED) {
      Collection<String> excludedProcesses = getExcludedProcesses();
      List<Path> processesToCheck = getProcessesToCheck();
      if (excludedProcesses != null &&
          ContainerUtil.all(processesToCheck, exe -> excludedProcesses.contains(exe.getFileName().toString().toLowerCase(Locale.ENGLISH))) &&
          excludedProcesses.contains("java.exe")) {
        LOG.info("Windows Defender status: all relevant processes excluded from real-time scanning");
        return new CheckResult(RealtimeScanningStatus.SCANNING_DISABLED, Collections.emptyMap());
      }

      List<Pattern> excludedPatterns = getExcludedPatterns();
      if (excludedPatterns != null) {
        Map<Path, Boolean> pathStatuses = checkPathsExcluded(getImportantPaths(project), excludedPatterns);
        boolean anyPathNotExcluded = !ContainerUtil.all(pathStatuses.values(), Boolean::booleanValue);
        if (anyPathNotExcluded) {
          LOG.info("Windows Defender status: some relevant paths not excluded from real-time scanning, notifying user");
        }
        else {
          LOG.info("Windows Defender status: all relevant paths excluded from real-time scanning");
        }
        return new CheckResult(scanningStatus, pathStatuses);
      }
      else {
        LOG.info("Windows Defender status: Failed to get excluded patterns");
        return new CheckResult(RealtimeScanningStatus.ERROR, Collections.emptyMap());
      }
    }
    if (scanningStatus == RealtimeScanningStatus.ERROR) {
      LOG.info("Windows Defender status: failed to detect");
    }
    else {
      LOG.info("Windows Defender status: real-time scanning disabled");
    }
    return new CheckResult(scanningStatus, Collections.emptyMap());
  }

  protected @NotNull List<Path> getProcessesToCheck() {
    List<Path> result = new ArrayList<>();
    Path ideStarter = Restarter.getIdeStarter();
    if (ideStarter != null) {
      result.add(ideStarter);
    }
    Path fsNotifier = NativeFileWatcherImpl.getFSNotifierExecutable();
    if (fsNotifier != null) {
      result.add(fsNotifier);
    }
    return result;
  }

  private static Boolean isWindowsDefenderActive() {
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(
        "wmic", "/Namespace:\\\\root\\SecurityCenter2", "Path", "AntivirusProduct", "Get", "displayName,productState"
      ), WMIC_COMMAND_TIMEOUT_MS);
      if (output.getExitCode() == 0) {
        return parseWindowsDefenderProductState(output);
      }
      else {
        LOG.warn("wmic Windows Defender check exited with status " + output.getExitCode() + ": " +
                 StringUtil.first(output.getStderr(), MAX_POWERSHELL_STDERR_LENGTH, false));
      }
    }
    catch (ExecutionException e) {
      LOG.warn("wmic Windows Defender check failed", e);
    }
    return null;
  }

  private static Boolean parseWindowsDefenderProductState(ProcessOutput output) {
    final String[] lines = StringUtil.splitByLines(output.getStdout());
    for (String line : lines) {
      if (line.startsWith("Windows Defender")) {
        final String productStateString = StringUtil.substringAfterLast(line, " ");
        int productState;
        try {
          productState = Integer.parseInt(productStateString);
          return (productState & 0x1000) != 0;
        }
        catch (NumberFormatException e) {
          LOG.info("Unexpected wmic output format: " + line);
          return null;
        }
      }
    }
    return false;
  }

  /** Runs a powershell command to list the paths that are excluded from realtime scanning by Windows Defender. These
   *
   * paths can contain environment variable references, as well as wildcards ('?', which matches a single character, and
   * '*', which matches any sequence of characters (but cannot match multiple nested directories; i.e., "foo\*\bar" would
   * match foo\baz\bar but not foo\baz\quux\bar)). The behavior of wildcards with respect to case-sensitivity is undocumented.
   * Returns a list of patterns, one for each exclusion path, that emulate how Windows Defender would interpret that path.
   */
  private static @Nullable List<Pattern> getExcludedPatterns() {
    final Collection<String> paths = getWindowsDefenderProperty("ExclusionPath");
    if (paths == null) return null;
    if (paths.size() > 0) {
      String path = paths.iterator().next();
      if (path.length() > 0 && path.indexOf('\\') < 0) {
        // "N/A: Must be admin to view exclusions"
        return null;
      }
    }
    return ContainerUtil.map(paths, path -> wildcardsToRegex(expandEnvVars(path)));
  }

  private static @Nullable Collection<String> getExcludedProcesses() {
    final Collection<String> processes = getWindowsDefenderProperty("ExclusionProcess");
    if (processes == null) return null;
    return ContainerUtil.map(processes, process -> process.toLowerCase());
  }

  /** Runs a powershell command to determine whether realtime scanning is enabled or not. */
  private static @NotNull RealtimeScanningStatus getRealtimeScanningEnabled() {
    final Collection<String> output = getWindowsDefenderProperty("DisableRealtimeMonitoring");
    if (output == null) return RealtimeScanningStatus.ERROR;
    if (output.size() > 0 && output.iterator().next().startsWith("False")) return RealtimeScanningStatus.SCANNING_ENABLED;
    return RealtimeScanningStatus.SCANNING_DISABLED;
  }

  private static @Nullable Collection<String> getWindowsDefenderProperty(final String propertyName) {
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(
        "powershell", "-inputformat", "none", "-outputformat", "text", "-NonInteractive", "-Command",
        "Get-MpPreference | select -ExpandProperty \"" + propertyName + "\""), POWERSHELL_COMMAND_TIMEOUT_MS);
      if (output.getExitCode() == 0) {
        return output.getStdoutLines();
      } else {
        LOG.warn("Windows Defender " + propertyName + " check exited with status " + output.getExitCode() + ": " +
                 StringUtil.first(output.getStderr(), MAX_POWERSHELL_STDERR_LENGTH, false));
      }
    } catch (ExecutionException e) {
      LOG.warn("Windows Defender " + propertyName + " check failed", e);
    }
    return null;
  }

  /** Returns a list of paths that might impact build performance if Windows Defender were configured to scan them. */
  protected @NotNull List<Path> getImportantPaths(@NotNull Project project) {
    String homeDir = System.getProperty("user.home");
    String gradleUserHome = System.getenv("GRADLE_USER_HOME");
    String projectDir = project.getBasePath();

    List<Path> paths = new ArrayList<>();
    if (projectDir != null) {
      paths.add(Paths.get(projectDir));
    }
    paths.add(Paths.get(PathManager.getSystemPath()));
    if (gradleUserHome != null) {
      paths.add(Paths.get(gradleUserHome));
    } else {
      paths.add(Paths.get(homeDir, ".gradle"));
    }

    return paths;
  }


  /** Expands references to environment variables (strings delimited by '%') in 'path' */
  private static @NotNull String expandEnvVars(@NotNull String path) {
    Matcher m = WINDOWS_ENV_VAR_PATTERN.matcher(path);
    StringBuilder result = new StringBuilder();
    while (m.find()) {
      String value = System.getenv(m.group(1));
      if (value != null) {
        m.appendReplacement(result, Matcher.quoteReplacement(value));
      }
    }
    m.appendTail(result);
    return result.toString();
  }

  /**
   * Produces a {@link Pattern} that approximates how Windows Defender interprets the exclusion path {@code path}.
   * The path is split around wildcards; the non-wildcard portions are quoted, and regex equivalents of
   * the wildcards are inserted between them. See
   * https://docs.microsoft.com/en-us/windows/security/threat-protection/windows-defender-antivirus/configure-extension-file-exclusions-windows-defender-antivirus
   * for more details.
   */
  private static @NotNull Pattern wildcardsToRegex(@NotNull String path) {
    Matcher m = WINDOWS_DEFENDER_WILDCARD_PATTERN.matcher(path);
    StringBuilder sb = new StringBuilder();
    int previousWildcardEnd = 0;
    while (m.find()) {
      sb.append(Pattern.quote(path.substring(previousWildcardEnd, m.start())));
      if (m.group().equals("?")) {
        sb.append("[^\\\\]");
      } else {
        sb.append("[^\\\\]*");
      }
      previousWildcardEnd = m.end();
    }
    sb.append(Pattern.quote(path.substring(previousWildcardEnd)));
    sb.append(".*"); // technically this should only be appended if the path refers to a directory, not a file. This is difficult to determine.
    return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE); // CASE_INSENSITIVE is overly permissive. Being precise with this is more work than it's worth.
  }

  /**
   * Checks whether each of the given paths in {@code paths} is matched by some pattern in {@code excludedPatterns},
   * returning a map of the results.
   */
  private static @NotNull Map<Path, Boolean> checkPathsExcluded(@NotNull List<? extends Path> paths, @NotNull List<Pattern> excludedPatterns) {
    Map<Path, Boolean> result = new HashMap<>();
    for (Path path : paths) {
      if (!path.toFile().exists()) continue;

      try {
        String canonical = path.toRealPath().toString();
        boolean found = false;
        for (Pattern pattern : excludedPatterns) {
          if (pattern.matcher(canonical).matches() || pattern.matcher(path.toString()).matches()) {
            found = true;
            result.put(path, true);
            break;
          }
        }
        if (!found) {
          result.put(path, false);
        }
      } catch (IOException e) {
        LOG.warn("Windows Defender exclusion check couldn't get real path for " + path, e);
      }
    }
    return result;
  }

  public void configureActions(Project project, WindowsDefenderNotification notification) {
    notification.addAction(new WindowsDefenderFixAction(notification.getPaths()));

    notification.addAction(new NotificationAction(DiagnosticBundle.message("virus.scanning.dont.show.again")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        PropertiesComponent.getInstance().setValue(IGNORE_VIRUS_CHECK, "true");
      }
    });
  }

  public @NlsContexts.NotificationContent String getNotificationText(Set<? extends Path> nonExcludedPaths) {
    return DiagnosticBundle.message("virus.scanning.warn.message", StringUtil.join(nonExcludedPaths, "<br/>"));
  }

  public String getConfigurationInstructionsUrl() {
    return "https://intellij-support.jetbrains.com/hc/en-us/articles/360006298560";
  }

  public boolean runExcludePathsCommand(Project project, Collection<Path> paths) {
    try {
      final ProcessOutput output =
        ExecUtil.sudoAndGetOutput(new GeneralCommandLine("powershell", "-Command", "Add-MpPreference", "-ExclusionPath",
                                                         StringUtil
                                                           .join(paths, (path) -> StringUtil.wrapWithDoubleQuote(path.toString()), ",")),
                                  "");
      return output.getExitCode() == 0;
    }
    catch (IOException | ExecutionException e) {
      UIUtil.invokeLaterIfNeeded(() ->
       Messages.showErrorDialog(project, DiagnosticBundle.message("virus.scanning.fix.failed", e.getMessage()),
                                DiagnosticBundle.message("virus.scanning.fix.title")));
    }
    return false;
  }
}
