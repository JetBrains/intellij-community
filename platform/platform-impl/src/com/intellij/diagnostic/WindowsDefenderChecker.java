// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WindowsDefenderChecker {
  private static final Logger LOG = Logger.getInstance(WindowsDefenderChecker.class);

  private static final Pattern WINDOWS_ENV_VAR_PATTERN = Pattern.compile("%([^%]+?)%");
  private static final Pattern WINDOWS_DEFENDER_WILDCARD_PATTERN = Pattern.compile("[?*]");
  private static final int POWERSHELL_COMMAND_TIMEOUT_MS = 10000;
  private static final int MAX_POWERSHELL_STDERR_LENGTH = 500;

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

  public CheckResult checkWindowsDefender(@NotNull Project project) {
    RealtimeScanningStatus scanningStatus = getRealtimeScanningEnabled();
    if (scanningStatus == RealtimeScanningStatus.SCANNING_ENABLED) {
      List<Pattern> excludedPatterns = getExcludedPatterns();
      if (excludedPatterns != null) {
        Map<Path, Boolean> pathStatuses = checkPathsExcluded(getImportantPaths(project), excludedPatterns);
        return new CheckResult(scanningStatus, pathStatuses);
      }
    }
    return new CheckResult(scanningStatus, Collections.emptyMap());
  }

  /** Runs a powershell command to list the paths that are excluded from realtime scanning by Windows Defender. These
   * paths can contain environment variable references, as well as wildcards ('?', which matches a single character, and
   * '*', which matches any sequence of characters (but cannot match multiple nested directories; i.e., "foo\*\bar" would
   * match foo\baz\bar but not foo\baz\quux\bar)). The behavior of wildcards with respect to case-sensitivity is undocumented.
   * Returns a list of patterns, one for each exclusion path, that emulate how Windows Defender would interpret that path.
   */
  @Nullable
  private static List<Pattern> getExcludedPatterns() {
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(
        "powershell", "-inputformat", "none", "-outputformat", "text", "-NonInteractive", "-Command", "Get-MpPreference | select -ExpandProperty \"ExclusionPath\""), POWERSHELL_COMMAND_TIMEOUT_MS);
      if (output.getExitCode() == 0) {
        return output.getStdoutLines(true).stream().map(path -> wildcardsToRegex(expandEnvVars(path))).collect(Collectors.toList());
      } else {
        LOG.warn("Windows Defender exclusion path check exited with status " + output.getExitCode() + ": " +
                 StringUtil.first(output.getStderr(), MAX_POWERSHELL_STDERR_LENGTH, false));
      }
    } catch (ExecutionException e) {
      LOG.warn("Windows Defender exclusion path check failed", e);
    }
    return null;
  }


  /** Runs a powershell command to determine whether realtime scanning is enabled or not. */
  @NotNull
  private static RealtimeScanningStatus getRealtimeScanningEnabled() {
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(
        "powershell", "-inputformat", "none", "-outputformat", "text", "-NonInteractive", "-Command", "Get-MpPreference | select -ExpandProperty \"DisableRealtimeMonitoring\""), POWERSHELL_COMMAND_TIMEOUT_MS);
      if (output.getExitCode() == 0) {
        if (output.getStdout().startsWith("False")) return RealtimeScanningStatus.SCANNING_ENABLED;
        return RealtimeScanningStatus.SCANNING_DISABLED;
      } else {
        LOG.warn("Windows Defender realtime scanning status check exited with status " + output.getExitCode() + ": " +
                 StringUtil.first(output.getStderr(), MAX_POWERSHELL_STDERR_LENGTH, false));
      }
    } catch (ExecutionException e) {
      LOG.warn("Windows Defender realtime scanning status check failed", e);
    }
    return RealtimeScanningStatus.ERROR;
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


  @NotNull
  public static String getNotificationTextForNonExcludedPaths(@NotNull Map<Path, Boolean> pathStatuses) {
    StringBuilder sb = new StringBuilder();
    pathStatuses.entrySet().stream().filter(entry -> !entry.getValue()).forEach(entry -> sb.append("<br/>" + entry.getKey()));
    return sb.toString();
  }
}
