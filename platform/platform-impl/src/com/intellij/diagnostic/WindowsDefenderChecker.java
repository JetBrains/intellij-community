// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.WbemcliUtil;
import com.sun.jna.platform.win32.Ole32;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.openapi.util.NullableLazyValue.volatileLazyNullable;
import static java.util.Objects.requireNonNull;

/**
 * Sources:
 * <a href="https://learn.microsoft.com/en-us/microsoft-365/security/defender-endpoint/configure-extension-file-exclusions-microsoft-defender-antivirus">Defender Settings</a>,
 * <a href="https://learn.microsoft.com/en-us/powershell/module/defender/">Defender PowerShell Module</a>.
 */
public class WindowsDefenderChecker {
  private static final Logger LOG = Logger.getInstance(WindowsDefenderChecker.class);

  private static final String HELPER_SCRIPT_NAME = "defender-exclusions.ps1";
  private static final String SIG_MARKER = "# SIG # Begin signature block";
  private static final int WMIC_COMMAND_TIMEOUT_MS = 10_000, POWERSHELL_COMMAND_TIMEOUT_MS = 30_000;

  public static WindowsDefenderChecker getInstance() {
    return ApplicationManager.getApplication().getService(WindowsDefenderChecker.class);
  }

  private final NullableLazyValue<Path> myHelper = volatileLazyNullable(() -> {
    var candidate = PathManager.findBinFile(HELPER_SCRIPT_NAME);
    if (candidate != null) {
      try {
        if (Files.readString(candidate).contains(SIG_MARKER)) {
          return candidate;
        }
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }
    LOG.info("'" + HELPER_SCRIPT_NAME + (candidate == null ? "' is missing" : "' is unsigned"));
    return null;
  });

  public boolean isStatusCheckIgnored(@NotNull Project project) {
    return false;
  }

  /**
   * {@link Boolean#TRUE} means Defender is present, active, and real-time protection check is enabled.
   * {@link Boolean#FALSE} means something from the above list is not true.
   * {@code null} means the IDE cannot detect the status.
   */
  public @Nullable Boolean isRealTimeProtectionEnabled() {
    try {
      var comInit = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
      if (LOG.isDebugEnabled()) LOG.debug("CoInitializeEx: " + comInit);

      var avQuery = new WbemcliUtil.WmiQuery<>("Root\\SecurityCenter2", "AntivirusProduct", AntivirusProduct.class);
      var avResult = avQuery.execute(WMIC_COMMAND_TIMEOUT_MS);
      if (LOG.isDebugEnabled()) LOG.debug("results: " + avResult.getResultCount());
      for (var i = 0; i < avResult.getResultCount(); i++) {
        var name = avResult.getValue(AntivirusProduct.DisplayName, i);
        if (LOG.isDebugEnabled()) LOG.debug("DisplayName[" + i + "]: " + name + " (" + name.getClass().getName() + ')');
        if (name instanceof String s && s.contains("Windows Defender")) {
          var state = avResult.getValue(AntivirusProduct.ProductState, i);
          if (LOG.isDebugEnabled()) LOG.debug("ProductState: " + state + " (" + state.getClass().getName() + ')');
          var enabled = state instanceof Integer intState && (intState.intValue() & 0x1000) != 0;
          if (!enabled) return false;
          break;
        }
      }

      var statusQuery  = new WbemcliUtil.WmiQuery<>("Root\\Microsoft\\Windows\\Defender", "MSFT_MpComputerStatus", MpComputerStatus.class);
      var statusResult = statusQuery.execute(WMIC_COMMAND_TIMEOUT_MS);
      if (LOG.isDebugEnabled()) LOG.debug("results: " + statusResult.getResultCount());
      if (statusResult.getResultCount() != 1) return false;
      var rtProtection = statusResult.getValue(MpComputerStatus.RealTimeProtectionEnabled, 0);
      if (LOG.isDebugEnabled()) LOG.debug("RealTimeProtectionEnabled: " + rtProtection + " (" + rtProtection.getClass().getName() + ')');
      return Boolean.TRUE.equals(rtProtection);
    }
    catch (Exception e) {
      if (e instanceof COMException ce && ce.matchesErrorCode(0x8004100e)) {  // WBEM_E_INVALID_NAMESPACE
        return false;
      }
      LOG.warn("WMI Windows Defender check failed", e);
      return null;
    }
  }

  final boolean canRunScript() {
    return myHelper.getValue() != null;
  }

  private enum AntivirusProduct {DisplayName, ProductState}
  private enum MpComputerStatus {RealTimeProtectionEnabled}

  /** Returns a list of paths that might impact build performance if Windows Defender were configured to scan them. */
  protected @NotNull List<Path> getImportantPaths(@NotNull Project project) {
    var paths = new ArrayList<Path>();

    var projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir != null && projectDir.getFileSystem() instanceof LocalFileSystem) {
      paths.add(projectDir.toNioPath());
    }

    paths.add(PathManager.getSystemDir());

    var gradleUserHome = System.getenv("GRADLE_USER_HOME");
    if (gradleUserHome != null) {
      paths.add(Path.of(gradleUserHome));
    }
    else {
      paths.add(Path.of(System.getProperty("user.home"), ".gradle"));
    }

    return paths;
  }

  final boolean excludeProjectPaths(@NotNull List<Path> paths) {
    try {
      var script = requireNonNull(myHelper.getValue(), "missing/dysfunctional helper");

      var psh = PathEnvironmentVariableUtil.findInPath("powershell.exe");
      if (psh == null) {
        LOG.info("no 'powershell.exe' on " + PathEnvironmentVariableUtil.getPathVariableValue());
        return false;
      }
      var sane = Stream.of("SystemRoot", "ProgramFiles").map(System::getenv).anyMatch(val -> val != null && psh.toPath().startsWith(val));
      if (!sane) {
        LOG.info("suspicious 'powershell.exe' location: " + psh);
        return false;
      }

      var command = new GeneralCommandLine(psh.getPath(), "-NonInteractive", "-Command", "(Get-AuthenticodeSignature '" + script + "').Status");
      var output = run(command);
      if (output.getExitCode() != 0 || !"Valid".equals(output.getStdout().trim())) {
        LOG.info("validation failed:\n[" + output.getExitCode() + "] " + command + "\noutput: " + output.getStdout().trim());
        return false;
      }

      command = ExecUtil.sudoCommand(
        new GeneralCommandLine(Stream.concat(
          Stream.of(psh.getPath(), "-ExecutionPolicy", "Bypass", "-NonInteractive", "-File", script.toString()),
          paths.stream().map(Path::toString)
        ).toList()),
        "");
      output = run(command);
      if (output.getExitCode() != 0) {
        LOG.info("script failed:\n[" + output.getExitCode() + "] " + command + "\noutput: " + output.getStdout().trim());
        return false;
      }
      else {
        LOG.info("OK; script output:\n" + output.getStdout().trim());
        return true;
      }
    }
    catch (Exception e) {
      LOG.warn(e);
      return false;
    }
  }

  private static ProcessOutput run(GeneralCommandLine command) throws ExecutionException {
    return ExecUtil.execAndGetOutput(
      command.withRedirectErrorStream(true).withWorkDirectory(PathManager.getTempPath()),
      POWERSHELL_COMMAND_TIMEOUT_MS);
  }

  public String getConfigurationInstructionsUrl() {
    return "https://intellij-support.jetbrains.com/hc/en-us/articles/360006298560";
  }
}
