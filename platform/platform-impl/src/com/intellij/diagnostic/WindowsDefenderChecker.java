// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
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
  private static final String IGNORE_VIRUS_CHECK = "ignore.virus.scanning.warn.message";

  public enum RealtimeScanningStatus {
    SCANNING_DISABLED,
    SCANNING_ENABLED,
    ERROR
  }

  public static WindowsDefenderChecker getInstance() {
    return ServiceManager.getService(WindowsDefenderChecker.class);
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
      return new CheckResult(RealtimeScanningStatus.SCANNING_DISABLED, Collections.emptyMap());
    }

    RealtimeScanningStatus scanningStatus = getRealtimeScanningEnabled();
    if (scanningStatus == RealtimeScanningStatus.SCANNING_ENABLED) {
      final Collection<String> processes = getExcludedProcesses();
      final String binaryName = Restarter.getCurrentProcessExecutableName();
      if (binaryName != null && processes != null &&
          processes.contains(StringUtil.substringAfterLast(binaryName.toLowerCase(), "\\")) &&
          processes.contains("java.exe")) {
        return new CheckResult(RealtimeScanningStatus.SCANNING_DISABLED, Collections.emptyMap());
      }

      List<Pattern> excludedPatterns = getExcludedPatterns();
      if (excludedPatterns != null) {
        Map<Path, Boolean> pathStatuses = checkPathsExcluded(getImportantPaths(project), excludedPatterns);
        return new CheckResult(scanningStatus, pathStatuses);
      }
    }
    return new CheckResult(scanningStatus, Collections.emptyMap());
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
  @Nullable
  private static List<Pattern> getExcludedPatterns() {
    final Collection<String> paths = getWindowsDefenderProperty("ExclusionPath");
    if (paths == null) return null;
    return ContainerUtil.map(paths, path -> wildcardsToRegex(expandEnvVars(path)));
  }

  @Nullable
  private static Collection<String> getExcludedProcesses() {
    final Collection<String> processes = getWindowsDefenderProperty("ExclusionProcess");
    if (processes == null) return null;
    return ContainerUtil.map(processes, process -> process.toLowerCase());
  }

  /** Runs a powershell command to determine whether realtime scanning is enabled or not. */
  @NotNull
  private static RealtimeScanningStatus getRealtimeScanningEnabled() {
    final Collection<String> output = getWindowsDefenderProperty("DisableRealtimeMonitoring");
    if (output == null) return RealtimeScanningStatus.ERROR;
    if (output.size() > 0 && output.iterator().next().startsWith("False")) return RealtimeScanningStatus.SCANNING_ENABLED;
    return RealtimeScanningStatus.SCANNING_DISABLED;
  }

  @Nullable
  private static Collection<String> getWindowsDefenderProperty(final String propertyName) {
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
  @NotNull
  protected List<Path> getImportantPaths(@NotNull Project project) {
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
  @NotNull
  private static String expandEnvVars(@NotNull String path) {
    Matcher m = WINDOWS_ENV_VAR_PATTERN.matcher(path);
    StringBuffer result = new StringBuffer();
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
   * Produces a {@link Pattern} that approximates how Windows Defender interprets the exclusion path {@link path}.
   * The path is split around wildcards; the non-wildcard portions are quoted, and regex equivalents of
   * the wildcards are inserted between them. See
   * https://docs.microsoft.com/en-us/windows/security/threat-protection/windows-defender-antivirus/configure-extension-file-exclusions-windows-defender-antivirus
   * for more details.
   */
  @NotNull
  private static Pattern wildcardsToRegex(@NotNull String path) {
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
   * Checks whether each of the given paths in {@link paths} is matched by some pattern in {@link excludedPatterns},
   * returning a map of the results.
   */
  @NotNull
  private static Map<Path, Boolean> checkPathsExcluded(@NotNull List<Path> paths, @NotNull List<Pattern> excludedPatterns) {
    Map<Path, Boolean> result = new HashMap<>();
    for (Path path : paths) {
      try {
        String canonical = path.toRealPath().toString();
        boolean found = false;
        for (Pattern pattern : excludedPatterns) {
          if (pattern.matcher(canonical).matches()) {
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
    notification.addAction(new NotificationAction(DiagnosticBundle.message("virus.scanning.dont.show.again.this.project")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        PropertiesComponent.getInstance(project).setValue(IGNORE_VIRUS_CHECK, "true");
      }
    });

  }

  public String getConfigurationInstructionsUrl() {
    // TODO Provide a better article
    return "https://intellij-support.jetbrains.com/hc/en-us/articles/360005028939-Slow-startup-on-Windows-splash-screen-appears-in-more-than-20-seconds";
  }

  public boolean runExcludePathsCommand(Project project, Collection<Path> paths) {
    try {
      ExecUtil.sudoAndGetOutput(new GeneralCommandLine("powershell", "-Command", "Add-MpPreference", "-ExclusionPath",
                                                       StringUtil.join(paths, (path) -> StringUtil.wrapWithDoubleQuote(path.toString()), ",")), "");
      return true;
    }
    catch (IOException | ExecutionException e) {
      UIUtil.invokeLaterIfNeeded(() ->
       Messages.showErrorDialog(project, DiagnosticBundle.message("virus.scanning.fix.failed", e.getMessage()),
                                DiagnosticBundle.message("virus.scanning.fix.title")));
    }
    return false;
  }
}
