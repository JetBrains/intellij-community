// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.system.WindowsComException;
import com.intellij.util.system.WindowsFileSystem;
import com.intellij.util.system.WindowsShell;
import com.intellij.util.system.WindowsWmi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Sources:
/// [Defender Settings](https://learn.microsoft.com/en-us/microsoft-365/security/defender-endpoint/configure-extension-file-exclusions-microsoft-defender-antivirus),
/// [Defender PowerShell Module](https://learn.microsoft.com/en-us/powershell/module/defender/).
@SuppressWarnings("MethodMayBeStatic")
public class WindowsDefenderChecker {
  private static final Logger LOG = Logger.getInstance(WindowsDefenderChecker.class);

  private static final String IGNORE_STATUS_CHECK = "ignore.virus.scanning.warn.message";
  private static final String HELPER_SCRIPT_NAME = "defender-exclusions.ps1";
  private static final int WMIC_COMMAND_TIMEOUT_MS = 10_000, POWERSHELL_COMMAND_TIMEOUT_MS = 60_000;
  private static final ExtensionPointName<Extension> EP_NAME = ExtensionPointName.create("com.intellij.defender.config");

  /// Use the extension to propose technology-specific paths (e.g., `$GRADLE_USER_HOME`) to be added to the Defender's exclusion list.
  public interface Extension {
    @NotNull Collection<Path> getPaths(@Nullable Project project, @Nullable Path projectPath);
  }

  public static WindowsDefenderChecker getInstance() {
    return ApplicationManager.getApplication().getService(WindowsDefenderChecker.class);
  }

  enum ProjectStatus {SKIPPED, SUCCEED, FAILED}

  private final Map<Path, @Nullable ProjectStatus> myProjectPaths = Collections.synchronizedMap(new HashMap<>());
  private volatile boolean myWmiAvailable = true;

  public final boolean isStatusCheckIgnored(@Nullable Project project) {
    return (
      !Registry.is("ide.check.windows.defender.rules") ||
      PropertiesComponent.getInstance().isTrueValue(IGNORE_STATUS_CHECK) ||
      (project != null && PropertiesComponent.getInstance(project).isTrueValue(IGNORE_STATUS_CHECK))
    );
  }

  public final void ignoreStatusCheck(@Nullable Project project, boolean ignore) {
    logCaller("ignore=" + ignore + " scope=" + (project == null ? "global" : project));
    var component = project == null ? PropertiesComponent.getInstance() : PropertiesComponent.getInstance(project);
    if (ignore) {
      component.setValue(IGNORE_STATUS_CHECK, true);
    }
    else {
      component.unsetValue(IGNORE_STATUS_CHECK);
    }
  }

  @ApiStatus.Internal
  public final void markProjectPath(@NotNull Path projectPath, boolean skip) {
    myProjectPaths.put(projectPath, skip ? ProjectStatus.SKIPPED : null);
  }

  @ApiStatus.Internal
  @RequiresBackgroundThread
  final @Nullable ProjectStatus isAlreadyProcessed(@NotNull Project project) {
    var projectPath = getProjectPath(project);
    if (projectPath != null && myProjectPaths.containsKey(projectPath)) {
      while (!project.isDisposed() && myProjectPaths.get(projectPath) == null) TimeoutUtil.sleep(100);
      var status = myProjectPaths.remove(projectPath);
      if (status == ProjectStatus.SUCCEED) {
        PropertiesComponent.getInstance(project).setValue(IGNORE_STATUS_CHECK, true);
      }
      return status;
    }

    return null;
  }

  private static @Nullable Path getProjectPath(Project project) {
    var basePath = project.getBasePath();
    if (basePath != null) return Path.of(basePath);
    var projectDir = ProjectUtil.guessProjectDir(project);
    return projectDir != null && projectDir.isInLocalFileSystem() ? projectDir.toNioPath() : null;
  }

  /// [Boolean#TRUE] means Defender is present, active, and the real-time protection check is enabled.
  /// [Boolean#FALSE] means something from the above list is not true.
  /// `null` means the IDE cannot detect the status.
  public final @Nullable Boolean isRealTimeProtectionEnabled() {
    if (!SystemInfo.isWindows || !myWmiAvailable) {
      return null;
    }

    try {
      var avResult = WindowsWmi.query("Root\\SecurityCenter2", "AntivirusProduct", List.of("DisplayName", "ProductState"), WMIC_COMMAND_TIMEOUT_MS);
      if (LOG.isDebugEnabled()) LOG.debug("AntivirusProduct: " + avResult.size());
      for (var row : avResult) {
        var name = row.get("DisplayName");
        if (LOG.isDebugEnabled()) LOG.debug("DisplayName: " + name);
        if (name instanceof String s && (s.contains("Windows Defender") || s.contains("Microsoft Defender"))) {
          var state = row.get("ProductState");
          if (LOG.isDebugEnabled()) LOG.debug("ProductState: " + state);
          var enabled = state instanceof Integer intState && (intState.intValue() & 0x1000) != 0;
          if (!enabled) return false;
          break;
        }
      }

      var statusResult = WindowsWmi.query("Root\\Microsoft\\Windows\\Defender", "MSFT_MpComputerStatus",
                                         List.of("RealTimeProtectionEnabled"), WMIC_COMMAND_TIMEOUT_MS);
      if (LOG.isDebugEnabled()) LOG.debug("MSFT_MpComputerStatus: " + statusResult.size());
      if (statusResult.size() != 1) return false;
      var rtProtection = statusResult.getFirst().get("RealTimeProtectionEnabled");
      if (LOG.isDebugEnabled()) LOG.debug("RealTimeProtectionEnabled: " + rtProtection);
      return Boolean.TRUE.equals(rtProtection);
    }
    catch (WindowsComException e) {
      // reference: https://learn.microsoft.com/en-us/windows/win32/wmisdk/wmi-error-constants
      if (e.hresult == 0x8004100E) return false;
      LOG.warn("WMI Microsoft Defender check failed [0x" + Integer.toHexString(e.hresult) + ']', e);
      return null;
    }
    catch (LinkageError failure) {
      myWmiAvailable = false;
      LOG.warn("WMI bindings are unavailable", failure);
      return null;
    }
    catch (Exception e) {
      LOG.warn("WMI Microsoft Defender check failed", e);
      return null;
    }
  }

  /// Returns `true` if the given path should not be put on the Defender's exclusion list
  /// (either because it may host suspicious files or is too broad).
  public final boolean isUntrustworthyLocation(@NotNull Path path) {
    if (path.getParent() == null) {
      return true;
    }

    var homeDir = Path.of(System.getProperty("user.home"));
    if (homeDir.startsWith(path)) {
      return true;
    }

    var tempVar = System.getenv("TEMP");
    if (tempVar != null) {
      var tempDir = Path.of(tempVar);
      if (path.startsWith(tempDir) || tempDir.startsWith(path)) {
        return true;
      }
    }

    var downloadDir = (Path)null;
    try {
      var knownFolder = WindowsShell.knownFolderPath(WindowsShell.FOLDERID_DOWNLOADS);
      if (knownFolder != null) downloadDir = Path.of(knownFolder);
    }
    catch (Exception e) {
      LOG.warn("download dir detection failed", e);
    }
    if (downloadDir == null) {
      downloadDir = homeDir.resolve("Downloads");
    }
    if (path.startsWith(downloadDir)) {
      return true;
    }

    return false;
  }

  public final @NotNull List<Path> getPathsToExclude(@NotNull Project project) {
    var paths = doGetPathsToExclude(project, null);
    var projectPath = getProjectPath(project);
    if (projectPath != null) {
      paths.add(projectPath);
    }
    return new ArrayList<>(paths);
  }

  public final @NotNull List<Path> getPathsToExclude(@Nullable Project project, @NotNull Path projectPath) {
    var paths = doGetPathsToExclude(project, projectPath);
    paths.add(projectPath);
    return new ArrayList<>(paths);
  }

  private Set<Path> doGetPathsToExclude(@Nullable Project project, @Nullable Path projectPath) {
    var paths = new TreeSet<Path>();
    paths.add(PathManager.getSystemDir());
    EP_NAME.forEachExtensionSafe(ext -> {
      paths.addAll(ext.getPaths(project, projectPath));
    });
    return paths;
  }

  public final @NotNull List<Path> filterDevDrivePaths(@NotNull List<Path> paths) {
    if (paths.isEmpty()) return paths;

    var buildNumber = SystemInfo.getWinBuildNumber();
    if (buildNumber == null || buildNumber < 22621) {
      if (LOG.isDebugEnabled()) LOG.debug("DevDrive feature is not supported on " + buildNumber);
      return paths;
    }

    try {
      return paths.stream().filter(path -> !WindowsFileSystem.isOnDevDrive(path)).toList();
    }
    catch (Exception e) {
      LOG.warn("DevDrive detection failed", e);
      return paths;
    }
  }

  public final boolean excludeProjectPaths(@NotNull Project project, @NotNull List<Path> paths) {
    return doExcludeProjectPaths(project, null, paths);
  }

  @ApiStatus.Internal
  public final boolean excludeProjectPaths(@Nullable Project project, @NotNull Path projectPath, @NotNull List<Path> paths) {
    return doExcludeProjectPaths(project, projectPath, paths);
  }

  private boolean doExcludeProjectPaths(@Nullable Project project, @Nullable Path projectPath, List<Path> paths) {
    logCaller("paths=" + paths + " project=" + (project != null ? project : projectPath));

    var result = ProjectStatus.FAILED;
    try {
      var script = PathManager.findBinFile(HELPER_SCRIPT_NAME);
      if (script == null) {
        LOG.info("'%s' is missing from '%s'".formatted(HELPER_SCRIPT_NAME, PathManager.getBinDir()));
        return false;
      }

      var psh = PathEnvironmentVariableUtil.findFirst("powershell.exe");
      if (psh == null) psh = PathEnvironmentVariableUtil.findFirst("pwsh.exe");
      if (psh == null) {
        LOG.info("no 'powershell.exe' or 'pwsh.exe' on " + PathEnvironmentVariableUtil.getPathVariableValue());
        return false;
      }
      var _psh = psh;
      var sane = Stream.of("SystemRoot", "ProgramFiles").map(System::getenv).anyMatch(val -> val != null && _psh.startsWith(val));
      if (!sane) {
        LOG.info("suspicious 'powershell.exe' location: " + psh);
        return false;
      }

      var scriptlet = "(Get-AuthenticodeSignature '" + script.toString().replace("'", "''") + "').Status";
      var command = new ProcessBuilder(psh.toString(), "-NoProfile", "-NonInteractive", "-Command", scriptlet);
      var start = System.nanoTime();
      var output = run(command, Charset.defaultCharset());
      if (output.getExitCode() != 0) {
        logProcessError("validation failed", command, start, output);
        return false;
      }
      var status = output.getStdout().trim();
      if ("NotSigned".equals(status) && ApplicationInfo.getInstance().getBuild().isSnapshot()) {
        LOG.info("allowing unsigned helper in dev. build " + ApplicationInfo.getInstance().getBuild());
      }
      else if (!"Valid".equals(status)) {
        LOG.info("validation failed: status='" + status + "'");
        return false;
      }

      var launcher = PathManager.findBinFileWithException("launcher.exe");
      command = new ProcessBuilder(Stream.concat(
        Stream.of(launcher.toString(), psh.toString(), "-ExecutionPolicy", "Bypass", "-NoProfile", "-NonInteractive", "-File", script.toString()),
        paths.stream().map(Path::toString)
      ).toList());
      start = System.nanoTime();
      output = run(command, StandardCharsets.UTF_8);
      if (output.getExitCode() != 0) {
        logProcessError("exclusion failed", command, start, output);
        return false;
      }
      else {
        LOG.info("OK; script output:\n" + output.getStdout().trim());
        if (project != null) {
          PropertiesComponent.getInstance(project).setValue(IGNORE_STATUS_CHECK, true);
        }
        result = ProjectStatus.SUCCEED;
        return true;
      }
    }
    catch (Exception e) {
      LOG.warn(e);
      return false;
    }
    finally {
      if (project == null) {
        myProjectPaths.put(projectPath, result);
      }
    }
  }

  @SuppressWarnings("IO_FILE_USAGE")
  private static ProcessOutput run(ProcessBuilder command, Charset charset) throws IOException {
    var tempDir = NioFiles.createDirectories(PathManager.getTempDir());
    command.environment().put("PSModulePath", "");
    command.redirectErrorStream(true);
    command.directory(tempDir.toFile());
    return new CapturingProcessHandler(command.start(), charset, "PowerShell")
      .runProcess(POWERSHELL_COMMAND_TIMEOUT_MS);
  }

  private static void logProcessError(String prefix, ProcessBuilder command, long start, ProcessOutput output) {
    var t = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    LOG.info(prefix + ":\n[" + output.getExitCode() + ", " + t + "ms] " + command.command() + "\noutput: " + output.getStdout().trim());
  }

  private static void logCaller(String prefix) {
    var options = EnumSet.of(StackWalker.Option.SHOW_HIDDEN_FRAMES, StackWalker.Option.SHOW_REFLECT_FRAMES);
    var trace = StackWalker.getInstance(options).walk(stack -> stack.skip(1).limit(10)
      .map(frame -> "  " + frame.toStackTraceElement())
      .collect(Collectors.joining("\n", prefix + "; called from:\n", "\n  ...")));
    LOG.info(trace);
  }

  public @NotNull String getConfigurationInstructionsUrl() {
    return "https://intellij.com/antivirus-impact-on-build-speed";
  }
}
