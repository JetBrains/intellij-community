// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.Wbemcli;
import com.sun.jna.platform.win32.COM.WbemcliUtil;
import com.sun.jna.platform.win32.Ole32;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sources:
 * <a href="https://learn.microsoft.com/en-us/microsoft-365/security/defender-endpoint/configure-extension-file-exclusions-microsoft-defender-antivirus">Defender Settings</a>,
 * <a href="https://learn.microsoft.com/en-us/powershell/module/defender/">Defender PowerShell Module</a>.
 */
@SuppressWarnings("MethodMayBeStatic")
public class WindowsDefenderChecker {
  private static final Logger LOG = Logger.getInstance(WindowsDefenderChecker.class);

  private static final String IGNORE_STATUS_CHECK = "ignore.virus.scanning.warn.message";
  private static final String HELPER_SCRIPT_NAME = "defender-exclusions.ps1";
  private static final int WMIC_COMMAND_TIMEOUT_MS = 10_000, POWERSHELL_COMMAND_TIMEOUT_MS = 30_000;
  private static final ExtensionPointName<Extension> EP_NAME = ExtensionPointName.create("com.intellij.defender.config");

  public interface Extension {
    @NotNull Collection<Path> getPaths(@NotNull Project project);
  }

  public static WindowsDefenderChecker getInstance() {
    return ApplicationManager.getApplication().getService(WindowsDefenderChecker.class);
  }

  public final boolean isStatusCheckIgnored(@NotNull Project project) {
    return !Registry.is("ide.check.windows.defender.rules") ||
           PropertiesComponent.getInstance().isTrueValue(IGNORE_STATUS_CHECK) ||
           PropertiesComponent.getInstance(project).isTrueValue(IGNORE_STATUS_CHECK);
  }

  public final void ignoreStatusCheck(@Nullable Project project, boolean ignore) {
    var component = project == null ? PropertiesComponent.getInstance() : PropertiesComponent.getInstance(project);
    if (ignore) {
      logCaller("scope=" + (project == null ? "global" : project));
      component.setValue(IGNORE_STATUS_CHECK, true);
    }
    else {
      component.unsetValue(IGNORE_STATUS_CHECK);
    }
  }

  /**
   * {@link Boolean#TRUE} means Defender is present, active, and real-time protection check is enabled.
   * {@link Boolean#FALSE} means something from the above list is not true.
   * {@code null} means the IDE cannot detect the status.
   */
  public final @Nullable Boolean isRealTimeProtectionEnabled() {
    if (!JnaLoader.isLoaded()) {
      LOG.debug("JNA is not loaded");
      return null;
    }

    try {
      var comInit = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
      if (LOG.isDebugEnabled()) LOG.debug("CoInitializeEx: " + comInit);

      var avQuery = new WbemcliUtil.WmiQuery<>("Root\\SecurityCenter2", "AntivirusProduct", AntivirusProduct.class);
      var avResult = avQuery.execute(WMIC_COMMAND_TIMEOUT_MS);
      if (LOG.isDebugEnabled()) LOG.debug("results: " + avResult.getResultCount());
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

      var statusQuery  = new WbemcliUtil.WmiQuery<>("Root\\Microsoft\\Windows\\Defender", "MSFT_MpComputerStatus", MpComputerStatus.class);
      var statusResult = statusQuery.execute(WMIC_COMMAND_TIMEOUT_MS);
      if (LOG.isDebugEnabled()) LOG.debug("results: " + statusResult.getResultCount());
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

  public final @NotNull List<Path> getPathsToExclude(@NotNull Project project) {
    var paths = new TreeSet<Path>();

    var projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir != null && projectDir.getFileSystem() instanceof LocalFileSystem) {
      paths.add(projectDir.toNioPath());
    }

    paths.add(PathManager.getSystemDir());

    EP_NAME.forEachExtensionSafe(ext -> {
      paths.addAll(ext.getPaths(project));
    });

    return new ArrayList<>(paths);
  }

  public final boolean excludeProjectPaths(@NotNull Project project, @NotNull List<Path> paths) {
    logCaller("paths=" + paths);

    try {
      var script = PathManager.findBinFile(HELPER_SCRIPT_NAME);
      if (script == null) {
        LOG.info("'" + HELPER_SCRIPT_NAME + "' is missing from '" + PathManager.getBinPath() + "'");
        return false;
      }

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

      var scriptlet = "(Get-AuthenticodeSignature '" + script + "').Status";
      var command = new ProcessBuilder(psh.getPath(), "-NoProfile", "-NonInteractive", "-Command", scriptlet);
      var output = run(command, Charset.defaultCharset());
      if (output.getExitCode() != 0) {
        LOG.info("validation failed:\n[" + output.getExitCode() + "] " + command.command() + "\noutput: " + output.getStdout().trim());
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
      output = run(command, StandardCharsets.UTF_8);
      if (output.getExitCode() != 0) {
        LOG.info("script failed:\n[" + output.getExitCode() + "] " + command.command() + "\noutput: " + output.getStdout().trim());
        return false;
      }
      else {
        LOG.info("OK; script output:\n" + output.getStdout().trim());
        PropertiesComponent.getInstance(project).setValue(IGNORE_STATUS_CHECK, true);
        return true;
      }
    }
    catch (Exception e) {
      LOG.warn(e);
      return false;
    }
  }

  private static ProcessOutput run(ProcessBuilder command, Charset charset) throws IOException {
    command.environment().put("PSModulePath", "");
    command.redirectErrorStream(true);
    command.directory(new File(PathManager.getTempPath()));
    return new CapturingProcessHandler(command.start(), charset, "PowerShell")
      .runProcess(POWERSHELL_COMMAND_TIMEOUT_MS);
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
