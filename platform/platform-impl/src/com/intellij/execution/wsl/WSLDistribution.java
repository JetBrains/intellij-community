// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialPromptDialog;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a single linux distribution in WSL, installed after <a href="https://blogs.msdn.microsoft.com/commandline/2017/10/11/whats-new-in-wsl-in-windows-10-fall-creators-update/">Fall Creators Update</a>
 *
 * @see WSLUtil
 */
public class WSLDistribution {
  static final String WSL_MNT_ROOT = "/mnt/";
  private static final int RESOLVE_SYMLINK_TIMEOUT = 10000;
  private static final String RUN_PARAMETER = "run";
  private static final Logger LOG = Logger.getInstance(WSLDistribution.class);

  private static final Key<ProcessListener> SUDO_LISTENER_KEY = Key.create("WSL sudo listener");

  @NotNull private final WslDistributionDescriptor myDescriptor;
  @NotNull private final Path myExecutablePath;

  protected WSLDistribution(@NotNull WSLDistribution dist) {
    this(dist.myDescriptor, dist.myExecutablePath);
  }

  WSLDistribution(@NotNull WslDistributionDescriptor descriptor, @NotNull Path executablePath) {
    myDescriptor = descriptor;
    myExecutablePath = executablePath;
  }

  /**
   * @return executable file
   */
  @NotNull
  public Path getExecutablePath() {
    return myExecutablePath;
  }

  /**
   * @return identification data of WSL distribution.
   */
  @Nullable
  public String readReleaseInfo() {
    try {
      final String key = "PRETTY_NAME";
      final String releaseInfo = "/etc/os-release"; // available for all distributions
      final ProcessOutput output = executeOnWsl(10000, "cat", releaseInfo);
      if (!output.checkSuccess(LOG)) return null;
      for (String line : output.getStdoutLines(true)) {
        if (line.startsWith(key) && line.length() >= (key.length() + 1)) {
          final String prettyName = line.substring(key.length() + 1);
          return  StringUtil.nullize(StringUtil.unquoteString(prettyName));
        }
      }
    }
    catch (ExecutionException e) {
      LOG.warn(e);
    }
    return null;
  }

  /**
   * @return creates and patches command line from args. e.g:
   * {@code ruby -v} => {@code bash -c "ruby -v"}
   */
  @NotNull
  public GeneralCommandLine createWslCommandLine(@NotNull String... args) {
    return patchCommandLine(new GeneralCommandLine(args), null, null, false);
  }

  /**
   * Creates a patched command line, executes it on wsl distribution and returns output
   *
   * @param timeout                timeout in ms
   * @param processHandlerConsumer consumes process handler just before execution, may be used for cancellation
   * @param args                   linux args, eg {@code gem env}
   */
  public ProcessOutput executeOnWsl(int timeout,
                                    @Nullable Consumer<ProcessHandler> processHandlerConsumer,
                                    @NotNull String... args) throws ExecutionException {
    GeneralCommandLine commandLine = createWslCommandLine(args);
    CapturingProcessHandler processHandler = new CapturingProcessHandler(commandLine);
    if (processHandlerConsumer != null) {
      processHandlerConsumer.consume(processHandler);
    }
    return WSLUtil.addInputCloseListener(processHandler).runProcess(timeout);
  }

  public ProcessOutput executeOnWsl(int timeout, @NotNull String... args) throws ExecutionException {
    return executeOnWsl(timeout, null, args);
  }

  public ProcessOutput executeOnWsl(@Nullable Consumer<ProcessHandler> processHandlerConsumer, @NotNull String... args)
    throws ExecutionException {
    return executeOnWsl(-1, processHandlerConsumer, args);
  }

  /**
   * Copying changed files recursively from wslPath/ to windowsPath/; with rsync
   *
   * @param wslPath           source path inside wsl, e.g. /usr/bin
   * @param windowsPath       target windows path, e.g. C:/tmp; Directory going to be created
   * @param additionalOptions may be used for --delete (not recommended), --include and so on
   * @param handlerConsumer   consumes process handler just before execution. Can be used for fast cancellation
   * @return process output
   */

  @SuppressWarnings("UnusedReturnValue")
  public ProcessOutput copyFromWsl(@NotNull String wslPath,
                                   @NotNull String windowsPath,
                                   @Nullable List<String> additionalOptions,
                                   @Nullable Consumer<ProcessHandler> handlerConsumer
  )
    throws ExecutionException {
    //noinspection ResultOfMethodCallIgnored
    new File(windowsPath).mkdirs();
    List<String> command = new ArrayList<>(Arrays.asList("rsync", "-cr"));

    if (additionalOptions != null) {
      command.addAll(additionalOptions);
    }

    command.add(wslPath + "/");
    String targetWslPath = getWslPath(windowsPath);
    if (targetWslPath == null) {
      throw new ExecutionException("Unable to copy files to " + windowsPath);
    }
    command.add(targetWslPath + "/");
    return executeOnWsl(handlerConsumer, ArrayUtil.toStringArray(command));
  }


  /**
   * Patches passed command line to make it runnable in WSL context, e.g changes {@code date} to {@code ubuntu run "date"}.<p/>
   * <p>
   * Environment variables and working directory are mapped to the chain calls: working dir using {@code cd} and environment variables using {@code export},
   * e.g {@code bash -c "export var1=val1 && export var2=val2 && cd /some/working/dir && date"}.<p/>
   * <p>
   * Method should properly handle quotation and escaping of the environment variables.<p/>
   *
   * @param commandLine      command line to patch
   * @param project          current project
   * @param remoteWorkingDir path to WSL working directory
   * @param askForSudo       true if we need to ask for sudo. To make this work, process handler, created from this command line should be patched using {@link #patchProcessHandler(GeneralCommandLine, ProcessHandler)}
   * @param <T>              GeneralCommandLine or descendant
   * @return original {@code commandLine}, prepared to run in WSL context
   */
  @NotNull
  public <T extends GeneralCommandLine> T patchCommandLine(@NotNull T commandLine,
                                                           @Nullable Project project,
                                                           @Nullable String remoteWorkingDir,
                                                           boolean askForSudo
  ) {
    Map<String, String> additionalEnvs = new THashMap<>(commandLine.getEnvironment());
    commandLine.getEnvironment().clear();

    LOG.info("[" + getId() + "] " +
             "Patching: " +
             commandLine.getCommandLineString() +
             "; working dir: " +
             remoteWorkingDir +
             "; envs: " +
             additionalEnvs.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", ")) +
             (askForSudo ? "; with sudo" : ": without sudo")
    );

    StringBuilder commandLineString = new StringBuilder();
    ParametersList parametersList = commandLine.getParametersList();
    List<String> realParamsList = parametersList.getList();

    // avoiding double wrapping into bash -c; may cause problems with escaping
    if (realParamsList.size() == 2 && "bash".equals(commandLine.getExePath()) && "-c".equals(realParamsList.get(0))) {
      commandLineString.append(realParamsList.get(1));
    }
    else {
      commandLineString.append(commandLine.getCommandLineString());
    }

    if (askForSudo) { // fixme shouldn't we sudo for every chunk? also, preserve-env, login?
      prependCommandLineString(commandLineString, "sudo", "-S", "-p", "''");
      //TODO[traff]: ask password only if it is needed. When user is logged as root, password isn't asked.

      SUDO_LISTENER_KEY.set(commandLine, new ProcessAdapter() {
        @Override
        public void startNotified(@NotNull ProcessEvent event) {
          OutputStream input = event.getProcessHandler().getProcessInput();
          if (input == null) {
            return;
          }
          String password = CredentialPromptDialog.askPassword(
            project,
            "Enter Root Password",
            "Sudo password for " + getPresentableName() + " root:",
            new CredentialAttributes("WSL", "root", WSLDistribution.class),
            true
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


    commandLine.setExePath(getExecutablePath().toString());
    parametersList.clearAll();
    parametersList.add(getRunCommandLineParameter());
    parametersList.add(commandLineString.toString());

    LOG.info("[" + getId() + "] " + "Patched as: " + commandLine.getCommandLineString());
    return commandLine;
  }

  @NotNull
  protected String getRunCommandLineParameter() {
    return RUN_PARAMETER;
  }

  /**
   * Attempts to resolve symlink with a given timeout
   *
   * @param path                  path in question
   * @param timeoutInMilliseconds timeout for execution
   * @return actual file name
   */
  @NotNull
  public String resolveSymlink(@NotNull String path, int timeoutInMilliseconds) {

    try {
      final ProcessOutput output = executeOnWsl(timeoutInMilliseconds, "readlink", "-f", path);
      if (output.getExitCode() == 0) {
        String stdout = output.getStdout().trim();
        if (output.getExitCode() == 0 && StringUtil.isNotEmpty(stdout)) {
          return stdout;
        }
      }
    }
    catch (ExecutionException e) {
      LOG.debug("Error while resolving symlink: " + path, e);
    }
    return path;
  }

  @NotNull
  public String resolveSymlink(@NotNull String path) {
    return resolveSymlink(path, RESOLVE_SYMLINK_TIMEOUT);
  }

  /**
   * Patches process handler with sudo listener, asking user for the password
   *
   * @param commandLine    patched command line
   * @param processHandler process handler, created from patched commandline
   * @return passed processHandler, patched with sudo listener if any
   */
  @NotNull
  public ProcessHandler patchProcessHandler(@NotNull GeneralCommandLine commandLine, @NotNull ProcessHandler processHandler) {
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
  public Map<String, String> getEnvironment() {
    try {
      ProcessOutput processOutput = executeOnWsl(5000, "env");
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

  /**
   * @return Windows-dependent path for a file, pointed by {@code wslPath} in WSL or null if path is unmappable
   */
  @Nullable
  public String getWindowsPath(@NotNull String wslPath) {
    return WSLUtil.getWindowsPath(wslPath);
  }

  /**
   * @return Linux path for a file pointed by {@code windowsPath} or null if unavailable, like \\MACHINE\path
   */
  @Nullable
  public String getWslPath(@NotNull String windowsPath) {
    if (FileUtil.isWindowsAbsolutePath(windowsPath)) { // absolute windows path => /mnt/disk_letter/path
      if (windowsPath.length() > 2) {
        char separator = windowsPath.charAt(2);
        if (separator != '/' && separator != '\\') {
          return null;
        }
      }
      return WSL_MNT_ROOT +
             Character.toLowerCase(windowsPath.charAt(0)) +
             FileUtil.toSystemIndependentName(windowsPath.substring(2));
    }
    return null;
  }

  @NotNull
  public String getId() {
    return myDescriptor.getId();
  }

  @NotNull
  public String getMsId() {
    return myDescriptor.getMsId();
  }

  @NotNull
  public String getPresentableName() {
    return myDescriptor.getPresentableName();
  }

  @Override
  public String toString() {
    return "WSLDistribution{" +
           "myDescriptor=" + myDescriptor +
           '}';
  }

  private static void prependCommandLineString(@NotNull StringBuilder commandLineString, @NotNull String... commands) {
    commandLineString.insert(0, createAdditionalCommand(commands) + " ");
  }

  private static String createAdditionalCommand(@NotNull String... commands) {
    return new GeneralCommandLine(commands).getCommandLineString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    WSLDistribution that = (WSLDistribution)o;

    if (!myDescriptor.equals(that.myDescriptor)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myDescriptor.hashCode();
  }
}
