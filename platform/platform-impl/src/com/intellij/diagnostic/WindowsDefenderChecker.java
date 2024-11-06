// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.jna.JnaLoader;
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
import com.sun.jna.Memory;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.Wbemcli;
import com.sun.jna.platform.win32.COM.WbemcliUtil;
import com.sun.jna.platform.win32.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sources:
 * <a href="https://learn.microsoft.com/en-us/microsoft-365/security/defender-endpoint/configure-extension-file-exclusions-microsoft-defender-antivirus">Defender Settings</a>,
 * <a href="https://learn.microsoft.com/en-us/powershell/module/defender/">Defender PowerShell Module</a>.
 */
@SuppressWarnings({"MethodMayBeStatic", "DuplicatedCode"})
public class WindowsDefenderChecker {
  private static final Logger LOG = Logger.getInstance(WindowsDefenderChecker.class);

  private static final String IGNORE_STATUS_CHECK = "ignore.virus.scanning.warn.message";
  private static final String HELPER_SCRIPT_NAME = "defender-exclusions.ps1";
  private static final int WMIC_COMMAND_TIMEOUT_MS = 10_000, POWERSHELL_COMMAND_TIMEOUT_MS = 30_000;
  private static final ExtensionPointName<Extension> EP_NAME = ExtensionPointName.create("com.intellij.defender.config");

  /**
   * Use the extension to propose technology-specific paths (e.g., {@code $GRADLE_USER_HOME}) to be added to the Defender's exclusion list.
   */
  public interface Extension {
    @NotNull Collection<Path> getPaths(@Nullable Project project, @Nullable Path projectPath);
  }

  public static WindowsDefenderChecker getInstance() {
    return ApplicationManager.getApplication().getService(WindowsDefenderChecker.class);
  }

  private final Map<Path, @Nullable Boolean> myProjectPaths = Collections.synchronizedMap(new HashMap<>());

  public final boolean isStatusCheckIgnored(@Nullable Project project) {
    return !Registry.is("ide.check.windows.defender.rules") ||
           PropertiesComponent.getInstance().isTrueValue(IGNORE_STATUS_CHECK) ||
           (project != null && PropertiesComponent.getInstance(project).isTrueValue(IGNORE_STATUS_CHECK));
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
  public final void markProjectPath(@NotNull Path projectPath) {
    myProjectPaths.put(projectPath, null);
  }

  @ApiStatus.Internal
  @RequiresBackgroundThread
  final boolean isAlreadyProcessed(@NotNull Project project) {
    var projectPath = getProjectPath(project);
    if (projectPath != null && myProjectPaths.containsKey(projectPath)) {
      while (!project.isDisposed() && myProjectPaths.get(projectPath) == null) TimeoutUtil.sleep(100);
      if (myProjectPaths.remove(projectPath) == Boolean.TRUE) {
        PropertiesComponent.getInstance(project).setValue(IGNORE_STATUS_CHECK, true);
      }
      return true;
    }

    return false;
  }

  private static @Nullable Path getProjectPath(Project project) {
    var projectDir = ProjectUtil.guessProjectDir(project);
    return projectDir != null && projectDir.isInLocalFileSystem() ? projectDir.toNioPath() : null;
  }

  /**
   * {@link Boolean#TRUE} means Defender is present, active, and real-time protection check is enabled.
   * {@link Boolean#FALSE} means something from the above list is not true.
   * {@code null} means the IDE cannot detect the status.
   */
  public final @Nullable Boolean isRealTimeProtectionEnabled() {
    if (!JnaLoader.isLoaded()) {
      LOG.debug("isRealTimeProtectionEnabled: JNA is not loaded");
      return null;
    }

    try {
      var comInit = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
      if (LOG.isDebugEnabled()) LOG.debug("CoInitializeEx: " + comInit);

      var avQuery = new WbemcliUtil.WmiQuery<>("Root\\SecurityCenter2", "AntivirusProduct", AntivirusProduct.class);
      var avResult = avQuery.execute(WMIC_COMMAND_TIMEOUT_MS);
      if (LOG.isDebugEnabled()) LOG.debug(avQuery.getWmiClassName() + ": " + avResult.getResultCount());
      for (var i = 0; i < avResult.getResultCount(); i++) {
        var name = avResult.getValue(AntivirusProduct.DisplayName, i);
        if (LOG.isDebugEnabled()) LOG.debug("DisplayName[" + i + "]: " + name + " (" + name.getClass().getName() + ')');
        if (name instanceof String s && (s.contains("Windows Defender") || s.contains("Microsoft Defender"))) {
          var state = avResult.getValue(AntivirusProduct.ProductState, i);
          if (LOG.isDebugEnabled()) LOG.debug("ProductState: " + state + " (" + state.getClass().getName() + ')');
          var enabled = state instanceof Integer intState && (intState.intValue() & 0x1000) != 0;
          if (!enabled) return false;
          break;
        }
      }

      var statusQuery = new WbemcliUtil.WmiQuery<>("Root\\Microsoft\\Windows\\Defender", "MSFT_MpComputerStatus", MpComputerStatus.class);
      var statusResult = statusQuery.execute(WMIC_COMMAND_TIMEOUT_MS);
      if (LOG.isDebugEnabled()) LOG.debug(statusQuery.getWmiClassName() + ": " + statusResult.getResultCount());
      if (statusResult.getResultCount() != 1) return false;
      var rtProtection = statusResult.getValue(MpComputerStatus.RealTimeProtectionEnabled, 0);
      if (LOG.isDebugEnabled()) LOG.debug("RealTimeProtectionEnabled: " + rtProtection + " (" + rtProtection.getClass().getName() + ')');
      return Boolean.TRUE.equals(rtProtection);
    }
    catch (COMException e) {
      // reference: https://learn.microsoft.com/en-us/windows/win32/wmisdk/wmi-error-constants
      if (e.matchesErrorCode(Wbemcli.WBEM_E_INVALID_NAMESPACE)) return false;  // Microsoft Defender not installed
      var message = "WMI Microsoft Defender check failed";
      var hresult = e.getHresult();
      if (hresult != null) message += " [0x" + Integer.toHexString(hresult.intValue()) + ']';
      LOG.warn(message, e);
      return null;
    }
    catch (Exception e) {
      LOG.warn("WMI Microsoft Defender check failed", e);
      return null;
    }
  }

  private enum AntivirusProduct {DisplayName, ProductState}
  private enum MpComputerStatus {RealTimeProtectionEnabled}

  public final boolean isUnderDownloads(@NotNull Path path) {
    var downloadDir = (Path)null;
    if (JnaLoader.isLoaded()) {
      try {
        downloadDir = Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Downloads));
      }
      catch (Exception e) {
        LOG.warn("download dir detection failed", e);
      }
    }
    if (downloadDir == null) {
      downloadDir = Path.of(System.getProperty("user.home"), "Downloads");
    }
    return path.startsWith(downloadDir);
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
    if (projectPath != null) {
      paths.add(projectPath);
    }
    EP_NAME.forEachExtensionSafe(ext -> {
      paths.addAll(ext.getPaths(project, projectPath));
    });
    return paths;
  }

  public final @NotNull List<Path> filterDevDrivePaths(@NotNull List<Path> paths) {
    if (paths.isEmpty()) return paths;

    if (!JnaLoader.isLoaded()) {
      LOG.debug("filterDevDrivePaths: JNA is not loaded");
      return paths;
    }

    var buildNumber = SystemInfo.getWinBuildNumber();
    if (buildNumber == null || buildNumber < 22621) {
      if (LOG.isDebugEnabled()) LOG.debug("DevDrive feature is not supported on " + buildNumber);
      return paths;
    }

    try (var volInfo = new FILE_FS_PERSISTENT_VOLUME_INFORMATION()) {
      return paths.stream().filter(path -> !isOnDevDrive(path, volInfo)).toList();
    }
    catch (Exception e) {
      LOG.warn("DevDrive detection failed", e);
      return paths;
    }
  }

  @SuppressWarnings("SpellCheckingInspection") private static final int FSCTL_QUERY_PERSISTENT_VOLUME_STATE = 0x9023C;
  private static final int PERSISTENT_VOLUME_STATE_DEV_VOLUME = 0x00002000;
  private static final int PERSISTENT_VOLUME_STATE_TRUSTED_VOLUME = 0x00004000;

  @SuppressWarnings({"unused", "FieldMayBeFinal"})
  @Structure.FieldOrder({"VolumeFlags", "FlagMask", "Version", "Reserved"})
  public static final class FILE_FS_PERSISTENT_VOLUME_INFORMATION extends Structure implements AutoCloseable {
    public int VolumeFlags;
    public int FlagMask;
    public int Version;
    public int Reserved;

    @Override
    public void close() {
      if (getPointer() instanceof Memory m) m.close();
    }
  }

  // https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/ntifs/ns-ntifs-_file_fs_persistent_volume_information
  private static boolean isOnDevDrive(Path path, FILE_FS_PERSISTENT_VOLUME_INFORMATION volInfo) {
    var handle = Kernel32.INSTANCE.CreateFile(
      path.toString(), WinNT.FILE_READ_ATTRIBUTES, WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE, null, WinNT.OPEN_EXISTING,
      WinNT.FILE_FLAG_BACKUP_SEMANTICS, null);
    if (handle == WinBase.INVALID_HANDLE_VALUE) {
      var err = Kernel32.INSTANCE.GetLastError();
      LOG.warn("CreateFile(" + path + "): " + err + ": " + Kernel32Util.formatMessageFromLastErrorCode(err));
      return false;
    }
    try {
      volInfo.FlagMask = PERSISTENT_VOLUME_STATE_DEV_VOLUME | PERSISTENT_VOLUME_STATE_TRUSTED_VOLUME;
      volInfo.Version = 1;
      volInfo.write();
      if (Kernel32.INSTANCE.DeviceIoControl(handle, FSCTL_QUERY_PERSISTENT_VOLUME_STATE,
                                            volInfo.getPointer(), volInfo.size(), volInfo.getPointer(), volInfo.size(), null, null)) {
        volInfo.read();
        if (LOG.isDebugEnabled()) LOG.debug(path + ": 0x" + Integer.toHexString(volInfo.VolumeFlags));
        return volInfo.VolumeFlags == (PERSISTENT_VOLUME_STATE_DEV_VOLUME | PERSISTENT_VOLUME_STATE_TRUSTED_VOLUME);
      }
      else {
        if (LOG.isDebugEnabled()) {
          var err = Kernel32.INSTANCE.GetLastError();
          LOG.debug("DeviceIoControl(" + path + "): " + err + ": " + Kernel32Util.formatMessageFromLastErrorCode(err));
        }
        return false;
      }
    }
    finally {
      Kernel32.INSTANCE.CloseHandle(handle);
    }
  }

  public final boolean excludeProjectPaths(@NotNull Project project, @NotNull List<Path> paths) {
    return doExcludeProjectPaths(project, null, paths);
  }

  public final boolean excludeProjectPaths(@Nullable Project project, @NotNull Path projectPath, @NotNull List<Path> paths) {
    return doExcludeProjectPaths(project, projectPath, paths);
  }

  private boolean doExcludeProjectPaths(@Nullable Project project, @Nullable Path projectPath, List<Path> paths) {
    logCaller("paths=" + paths + " project=" + (project != null ? project : projectPath));

    var result = Boolean.FALSE;
    try {
      var script = PathManager.findBinFile(HELPER_SCRIPT_NAME);
      if (script == null) {
        LOG.info("'" + HELPER_SCRIPT_NAME + "' is missing from '" + PathManager.getBinPath() + "'");
        return false;
      }

      var psh = PathEnvironmentVariableUtil.findInPath("powershell.exe");
      if (psh == null) psh = PathEnvironmentVariableUtil.findInPath("pwsh.exe");
      if (psh == null) {
        LOG.info("no 'powershell.exe' or 'pwsh.exe' on " + PathEnvironmentVariableUtil.getPathVariableValue());
        return false;
      }
      var pshPath = psh.toPath();
      var sane = Stream.of("SystemRoot", "ProgramFiles").map(System::getenv).anyMatch(val -> val != null && pshPath.startsWith(val));
      if (!sane) {
        LOG.info("suspicious 'powershell.exe' location: " + psh);
        return false;
      }

      var scriptlet = "(Get-AuthenticodeSignature '" + script.toString().replace("'", "''") + "').Status";
      var command = new ProcessBuilder(psh.getPath(), "-NoProfile", "-NonInteractive", "-Command", scriptlet);
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
        Stream.of(launcher.toString(), psh.getPath(), "-ExecutionPolicy", "Bypass", "-NoProfile", "-NonInteractive", "-File", script.toString()),
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
        result = Boolean.TRUE;
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

  private static ProcessOutput run(ProcessBuilder command, Charset charset) throws IOException {
    var tempDir = NioFiles.createDirectories(Path.of(PathManager.getTempPath()));
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
