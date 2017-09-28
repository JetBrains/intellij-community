// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.execution;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialPromptDialog;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.*;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WSLUtil {
  private static final Logger LOG = Logger.getInstance(WSLUtil.class);
  private static final String WSL_ROOT_CHUNK = "\\lxss\\rootfs";
  private static final String WSL_MNT_ROOT = "/mnt";
  private static final Pattern WIN_IN_WSL_PATH_PATTERN = Pattern.compile(WSL_MNT_ROOT + "/(.)(?:/(.*))?");
  private static final Key<ProcessListener> SUDO_LISTENER_KEY = Key.create("WSL sudo listener");

  /* https://msdn.microsoft.com/en-us/commandline/wsl/about */
  private static final AtomicNullableLazyValue<File> ourWSLBashFile = new AtomicNullableLazyValue<File>() {
    @Nullable
    @Override
    protected File compute() {
      if (SystemInfo.isWin10OrNewer) {
        String windir = System.getenv().get("windir");
        if (!StringUtil.isEmpty(windir)) {
          File bashFile = new File(windir + "\\System32\\bash.exe");
          if (bashFile.exists()) {
            return bashFile;
          }
        }
      }

      return null;
    }
  };
  private static final AtomicNullableLazyValue<String> WSL_ROOT_IN_WINDOWS_PROVIDER = AtomicNullableLazyValue.createValue(() -> {
    String localAppDataPath = System.getenv().get("LOCALAPPDATA");
    return StringUtil.isEmpty(localAppDataPath) ? null : localAppDataPath + WSL_ROOT_CHUNK;
  });

  /**
   * @return bash file or null if not exists
   */
  @Nullable
  public static File getWSLBashFile() {
    return ourWSLBashFile.getValue();
  }

  /**
   * @return true if WSL available on the system
   */
  public static boolean hasWSL() {
    return getWSLBashFile() != null;
  }

  private static final Pattern WIN_BUILD_VER = Pattern.compile(".*(?:\\[Version \\d+\\.\\d+\\.(\\d+)\\])");
  /*
   * WSL version equals Windows build number
   * (https://github.com/Microsoft/BashOnWindows/issues/1728)
   */
  private static final AtomicNullableLazyValue<String> ourWSLVersion = AtomicNullableLazyValue.createValue(() -> {
    final GeneralCommandLine cl = new GeneralCommandLine("cmd", "/c", "ver");
    cl.setCharset(CharsetToolkit.getDefaultSystemCharset());

    try {
      final CapturingProcessHandler handler = new CapturingProcessHandler(cl);
      final ProcessOutput result = handler.runProcess(1000);
      if (result.isTimeout()) return null;

      final String out = result.getStdout().trim();
      final Matcher dependency = WIN_BUILD_VER.matcher(out);
      if (dependency.find()) {
        return dependency.group(1);
      }
    }
    catch (ExecutionException ignored) {
      ignored.printStackTrace();
    }
    return null;
  });

  /**
   * @return WSL build number or null if it cannot be determined
   */
  public static String getWslVersion() {
    if (hasWSL()) {
      return ourWSLVersion.getValue();
    }
    return null;
  }

  /**
   * @return Windows-dependent path for a file, pointed by {@code wslPath} in WSL
   */
  public static String getWindowsPath(@NotNull String wslPath) {
    Matcher matcher = WIN_IN_WSL_PATH_PATTERN.matcher(wslPath);
    String result;
    if (matcher.matches()) { // it's a windows path inside WSL
      String path = matcher.group(2);
      result = matcher.group(1) + ":" + (path == null ? "//" : path);
    }
    else { // it's a lsxx path
      String wslRootInHost = WSL_ROOT_IN_WINDOWS_PROVIDER.getValue();
      if (wslRootInHost == null) {
        LOG.error("Unable to find WSL root");
        return wslPath;
      }
      result = wslRootInHost + wslPath;
    }
    return FileUtil.toSystemDependentName(result);
  }

  /**
   * @return Linux path for a file pointed by {@code windowsPath}
   */
  @NotNull
  public static String getWslPath(@NotNull String windowsPath) {
    String wslRootInHost = WSL_ROOT_IN_WINDOWS_PROVIDER.getValue();
    if (wslRootInHost == null) {
      LOG.error("Unable to find WSL root");
      return windowsPath;
    }

    String dependentWindowsPath = FileUtil.toSystemDependentName(windowsPath);

    if (dependentWindowsPath.startsWith(wslRootInHost)) {  // this is some internal WSL file
      return FileUtil.toSystemIndependentName(dependentWindowsPath.substring(wslRootInHost.length()));
    }
    else if (StringUtil.isChar(dependentWindowsPath, 1, ':')) { // normal windows path => /mnt/disk_letter/path
      return WSL_MNT_ROOT +
             "/" +
             Character.toLowerCase(dependentWindowsPath.charAt(0)) +
             FileUtil.toSystemIndependentName(dependentWindowsPath.substring(2));
    }
    // path, e.g. share \\MACHINE\path...
    return windowsPath;
  }

  /**
   * Patches passed command line to make it runnable in WSL context, e.g changes {@code date} to {@code bash -c "date"}.<p/>
   * <p>
   * Environment variables and working directory are mapped to the chain calls: working dir using {@code cd} and environment variables using {@code export},
   * e.g {@code bash -c "export var1=val1 && export var2=val2 && cd /some/working/dir && date"}.<p/>
   * <p>
   * Method should properly handle quotation and escaping of the environment variables.<p/>
   *
   * @param commandLine      command line to patch
   * @param project          current project
   * @param remoteWorkingDir path to WSL working directory
   * @param askForSudo       true if we need to ask for sudo. To make this work, process handler, created from this command line should be patched using {@link WSLUtil#patchProcessHandler(GeneralCommandLine, ProcessHandler)}
   * @param <T>              GeneralCommandLine or descendant
   * @return original {@code commandLine}, prepared to run in WSL context
   */
  @NotNull
  public static <T extends GeneralCommandLine> T patchCommandLine(@NotNull T commandLine,
                                                                  @Nullable Project project,
                                                                  @Nullable String remoteWorkingDir,
                                                                  boolean askForSudo
  ) {
    File bashFile = getWSLBashFile();
    if (bashFile == null || !bashFile.exists()) {
      return commandLine;
    }

    Map<String, String> additionalEnvs = new THashMap<>(commandLine.getEnvironment());
    commandLine.getEnvironment().clear();

    LOG.info("Patching: " +
             commandLine.getCommandLineString() +
             "; working dir: " +
             remoteWorkingDir +
             "; envs: " +
             additionalEnvs.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", ")) +
             (askForSudo ? "; with sudo" : ": without sudo")
    );

    StringBuilder commandLineString = new StringBuilder(commandLine.getCommandLineString());

    if (askForSudo) { // fixme shouldn't we sudo for every chunk? also, preserve-env, login?
      prependCommandLineString(commandLineString, "sudo", "-S", "-p", "''");
      //TODO[traff]: ask password only if it is needed. When user is logged as root, password isn't asked.

      SUDO_LISTENER_KEY.set(commandLine, new ProcessAdapter() {
        @Override
        public void startNotified(ProcessEvent event) {
          OutputStream input = event.getProcessHandler().getProcessInput();
          if (input == null) {
            return;
          }
          String password = CredentialPromptDialog.askPassword(
            project,
            "Enter Root Password",
            "Sudo password for WSL root:",
            new CredentialAttributes("WSL", "root", WSLUtil.class)
          );
          if (password != null) {
            PrintWriter pw = new PrintWriter(input);
            try {
              pw.println(password);
            }
            finally {
              pw.close();
            }
          }
          else {
            // fixme notify user?
          }
          super.startNotified(event);
        }
      });
    }

    if (StringUtil.isNotEmpty(remoteWorkingDir)) {
      prependCommandLineString(commandLineString, "cd", remoteWorkingDir, "&&");
    }

    additionalEnvs.forEach((key, val) -> {
      if (StringUtil.containsChar(val, '*') && !StringUtil.isQuotedString(val)) {
        val = "'" + val + "'";
      }
      prependCommandLineString(commandLineString, "export", key + "=" + val, "&&");
    });

    try {
      commandLine.setExePath(bashFile.getCanonicalPath());
      ParametersList parametersList = commandLine.getParametersList();
      parametersList.clearAll();
      parametersList.add("-c");
      parametersList.add(commandLineString.toString());
    }
    catch (IOException e) {
      LOG.error(e);
    }
    LOG.info("Patched as: " + commandLine.getCommandLineString());
    return commandLine;
  }

  private static void prependCommandLineString(@NotNull StringBuilder commandLineString, @NotNull String... commands) {
    commandLineString.insert(0, createAdditionalCommand(commands) + " ");
  }

  private static String createAdditionalCommand(@NotNull String... commands) {
    return new GeneralCommandLine(commands).getCommandLineString();
  }

  @NotNull
  public static String resolveSymlink(@NotNull String path) {
    final GeneralCommandLine cl = new GeneralCommandLine();
    cl.setExePath("readlink");
    cl.addParameter("-f");
    cl.addParameter(path);

    final GeneralCommandLine cmd = patchCommandLine(cl, null, null, false);

    try {
      final CapturingProcessHandler process = new CapturingProcessHandler(cmd);
      final ProcessOutput output = process.runProcess(1000);
      if (output.getExitCode() == 0) {
        return output.getStdout().trim();
      }
    }
    catch (ExecutionException ignored) {}
    return path;
  }

  /**
   * Patches process handler with sudo listener, asking user for the password
   *
   * @param commandLine    patched command line
   * @param processHandler process handler, created from patched commandline
   * @return passed processHandler, patched with sudo listener if any
   */
  @NotNull
  public static ProcessHandler patchProcessHandler(@NotNull GeneralCommandLine commandLine, @NotNull ProcessHandler processHandler) {
    ProcessListener listener = SUDO_LISTENER_KEY.get(commandLine);
    if (listener != null) {
      processHandler.addProcessListener(listener);
      SUDO_LISTENER_KEY.set(commandLine, null);
    }
    return processHandler;
  }

  /**
   * @return environment map of the default user in wsl
   */
  @NotNull
  public static Map<String, String> getWSLEnvironment() {
    File bashFile = getWSLBashFile();
    if (bashFile == null) {
      return Collections.emptyMap();
    }

    GeneralCommandLine commandLine = new GeneralCommandLine(bashFile.getAbsolutePath(), "-c", "env");
    try {
      ProcessOutput processOutput = ExecUtil.execAndGetOutput(commandLine);
      Map<String, String> result = new THashMap<>();
      for (String string : processOutput.getStdoutLines()) {
        int assignIndex = string.indexOf('=');
        if (assignIndex == -1) {
          result.put(string, "");
        }
        else {
          result.put(string.substring(0, assignIndex), string.substring(assignIndex + 1));
        }
      }
      return result;
    }
    catch (ExecutionException e) {
      LOG.warn(e);
    }

    return Collections.emptyMap();
  }
}
