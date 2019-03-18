// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialPromptDialog;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.*;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EnvironmentUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.EnvironmentUtil.BASH_EXECUTABLE_NAME;
import static com.intellij.util.EnvironmentUtil.SHELL_LOGIN_ARGUMENT;

/**
 * Represents a single linux distribution in WSL, installed after <a href="https://blogs.msdn.microsoft.com/commandline/2017/10/11/whats-new-in-wsl-in-windows-10-fall-creators-update/">Fall Creators Update</a>
 *
 * @see WSLUtil
 * @see WSLDistributionWithRoot
 */
public class WSLDistribution {
  static final String DEFAULT_WSL_MNT_ROOT = "/mnt/";
  private static final int RESOLVE_SYMLINK_TIMEOUT = 10000;
  private static final String RUN_PARAMETER = "run";
  private static final Logger LOG = Logger.getInstance(WSLDistribution.class);
  private static final Key<String> ORIGINAL_COMMAND_LINE = Key.create("wsl.original.command.line");
  private static final Key<String> WRAPPER_SCRIPT_TEXT = Key.create("wsl.wrapper.script.text");

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
      final ProcessOutput output = executeSimpleCommand(10000, "cat", releaseInfo);
      if (!output.checkSuccess(LOG)) return null;
      for (String line : output.getStdoutLines(true)) {
        if (line.startsWith(key) && line.length() >= (key.length() + 1)) {
          final String prettyName = line.substring(key.length() + 1);
          return StringUtil.nullize(StringUtil.unquoteString(prettyName));
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
   * Performs execution of simple command, like {@code env} or {@code pwd} with a {@code timeout} and returns result. Does not support
   * sudo, environment passing and so on.
   */
  public ProcessOutput executeSimpleCommand(int timeout, @NotNull String... args) throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine(getExecutablePath().toString(), getRunCommandLineParameter());
    commandLine.addParameters(args);
    return executeWslCommandLine(commandLine, timeout, null);
  }

  /**
   * Creates a patched command line, executes it on wsl distribution and returns output
   *
   * @param timeout                timeout in ms
   * @param processHandlerConsumer consumes process handler just before execution, may be used for cancellation
   * @param args                   linux args, eg {@code gem env}
   */
  public ProcessOutput executeOnWsl(int timeout,
                                    @Nullable Consumer<? super ProcessHandler> processHandlerConsumer,
                                    @NotNull String... args) throws ExecutionException {
    GeneralCommandLine commandLine = createWslCommandLine(args);
    return executeWslCommandLine(commandLine, timeout, processHandlerConsumer);
  }

  /**
   * Executes a wsl command line as it is with a {@code timeout}. Resulting processHandler fed into {@code processHandlerConsumer} if any
   *
   * @see #executeSimpleCommand(int, String...)
   */
  public ProcessOutput executeWslCommandLine(@NotNull GeneralCommandLine wslCommandLine,
                                             int timeout,
                                             @Nullable Consumer<? super ProcessHandler> processHandlerConsumer) throws ExecutionException {
    CapturingProcessHandler processHandler = new CapturingProcessHandler(wslCommandLine);
    if (processHandlerConsumer != null) {
      processHandlerConsumer.consume(processHandler);
    }
    return WSLUtil.addInputCloseListener(processHandler).runProcess(timeout);
  }

  public ProcessOutput executeOnWsl(int timeout, @NotNull String... args) throws ExecutionException {
    return executeOnWsl(timeout, null, args);
  }

  public ProcessOutput executeOnWsl(@Nullable Consumer<? super ProcessHandler> processHandlerConsumer, @NotNull String... args)
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
                                   @Nullable Consumer<? super ProcessHandler> handlerConsumer
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
   * Builds a shell script from the passed {@code commandLine}, makes a temporary file and changes the command line to run it in WSL.
   * E.g. {@code ubuntu run /path/to/script}
   *
   * @param askForSudo          true if we need to ask for sudo. To make this work, process handler, created from this command line should be patched using {@link #patchProcessHandler(GeneralCommandLine, ProcessHandler)}
   * @param useLoginShell       if true, wrapper script going to be executed with {@code bash -l -c}
   * @param useInteractiveShell if true, wrapper script going to be executed with {@code bash -i -c}
   * @return original {@code commandLine} object, changed to to run command provided in the WSL context
   * @apiNote <ul>
   * <li>Working directory changed using {@code cd} command in the shell script</li>
   * <li>Unquoted environment variables are going to be escaped (" symbol) and quoted with double quotes, quoted variables passed as is.</li>
   * <li>Original command line text may be obtained using {@link #getOriginalCommandLineText(GeneralCommandLine)}</li>
   * <li>Text of generated script may be obtained using {@link #getWrapperScript(GeneralCommandLine)}</li>
   * <li>If you are running script in the console, and would like original command line to be printed in it, use {@link #attachOriginalCommandLinePrinter(GeneralCommandLine, ProcessHandler)}</li>
   * </ul>
   */
  @NotNull
  public <T extends GeneralCommandLine> T patchCommandLine(@NotNull T commandLine,
                                                           @Nullable Project project,
                                                           @Nullable String remoteWorkingDir,
                                                           boolean askForSudo,
                                                           boolean useLoginShell,
                                                           boolean useInteractiveShell) {
    ORIGINAL_COMMAND_LINE.set(commandLine, commandLine.getCommandLineString());
    Map<String, String> additionalEnvs = new THashMap<>(commandLine.getEnvironment());
    commandLine.getEnvironment().clear();

    LOG.debug("[" + getId() + "] " +
              "Patching: " +
              commandLine.getCommandLineString() +
              "; working dir: " +
              remoteWorkingDir +
              "; envs: " +
              additionalEnvs.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", ")) +
              (askForSudo ? "; with sudo" : ": without sudo")
    );

    StringBuilder scriptBuilder = new StringBuilder("#!/bin/bash\n");

    if (StringUtil.isNotEmpty(remoteWorkingDir)) {
      scriptBuilder.append("cd ").append(remoteWorkingDir).append("\n");
    }

    additionalEnvs.forEach((key, val) -> {
      val = StringUtil.isQuotedString(val) ? val : '"' + StringUtil.escapeChar(val, '"') + '"';
      scriptBuilder.append("export ").append(key).append("=").append(val).append("\n");
    });

    StringBuilder commandLineBuilder = new StringBuilder(commandLine.getCommandLineString());

    if (askForSudo) { // fixme shouldn't we sudo for every chunk? also, preserve-env, login?
      commandLineBuilder.insert(0, "sudo -S -p '' ");
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
            try (PrintWriter pw = new PrintWriter(input)) {
              pw.println(password);
            }
          }
          else {
            // fixme notify user?
          }
          super.startNotified(event);
        }
      });
    }

    String wrapperScriptText = scriptBuilder.append(commandLineBuilder).toString();

    LOG.debug("Generated script: " + wrapperScriptText);
    File wrapperScript;
    try {
      wrapperScript = ExecUtil.createTempExecutableScript("wsl_command_wrapper", "", wrapperScriptText);
      WRAPPER_SCRIPT_TEXT.set(commandLine, wrapperScriptText);
    }
    catch (ExecutionException | IOException e) {
      LOG.error(e);
      return commandLine;
    }

    commandLine.setExePath(getExecutablePath().toString());
    ParametersList parametersList = commandLine.getParametersList();
    parametersList.clearAll();
    parametersList.add(getRunCommandLineParameter());
    if (useLoginShell || useInteractiveShell) {
      parametersList.add(BASH_EXECUTABLE_NAME);
      if (useInteractiveShell) {
        parametersList.add(EnvironmentUtil.SHELL_INTERACTIVE_ARGUMENT);
      }
      if (useLoginShell) {
        parametersList.add(SHELL_LOGIN_ARGUMENT);
      }
    }
    parametersList.add(getWslPath(wrapperScript.toString()));

    LOG.debug("[" + getId() + "] " + "Patched as: " + commandLine.getCommandLineString());
    return commandLine;
  }

  @NotNull
  public <T extends GeneralCommandLine> T patchCommandLine(@NotNull T commandLine,
                                                           @Nullable Project project,
                                                           @Nullable String remoteWorkingDir,
                                                           boolean askForSudo) {
    return patchCommandLine(commandLine, project, remoteWorkingDir, askForSudo, false, false);
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
      final ProcessOutput output = executeSimpleCommand(timeoutInMilliseconds, "readlink", "-f", path);
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
  public <T extends ProcessHandler> T patchProcessHandler(@NotNull GeneralCommandLine commandLine, @NotNull T processHandler) {
    ProcessListener listener = SUDO_LISTENER_KEY.get(commandLine);
    if (listener != null) {
      processHandler.addProcessListener(listener);
      SUDO_LISTENER_KEY.set(commandLine, null);
    }
    return processHandler;
  }

  /**
   * Patches process handler with listener, which prints original command line into the process handler like:<br/>
   * <code>
   * Executing: &lt;original command line&gt;<br/>
   * as
   * </code>
   *
   * @return passed processHandler
   * @apiNote this listener is expected to be added before console one. In such case, actual command line going to be printed immediately
   * after {@code as}
   */
  @NotNull
  public <T extends ProcessHandler> T attachOriginalCommandLinePrinter(@NotNull GeneralCommandLine commandLine, @NotNull T processHandler) {
    String executablePath = commandLine.getExePath();
    String originalCommandLineText = getOriginalCommandLineText(commandLine);
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (ProcessOutputType.isSystem(outputType) && StringUtil.startsWith(event.getText(), executablePath)) {
          if (StringUtil.isNotEmpty(originalCommandLineText)) {
            processHandler.notifyTextAvailable(IdeBundle.message("wsl.executing.with", originalCommandLineText), outputType);
          }
        }
        super.onTextAvailable(event, outputType);
      }
    });
    return processHandler;
  }

  /**
   * @return environment map of the default user in wsl
   */
  @NotNull
  public Map<String, String> getEnvironment() {
    try {
      ProcessOutput processOutput = executeSimpleCommand(5000, "env");
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
    return WSLUtil.getWindowsPath(wslPath, myDescriptor.getMntRoot());
  }

  /**
   * @return Linux path for a file pointed by {@code windowsPath} or null if unavailable, like \\MACHINE\path
   */
  @Nullable
  public String getWslPath(@NotNull String windowsPath) {
    if (FileUtil.isWindowsAbsolutePath(windowsPath)) { // absolute windows path => /mnt/disk_letter/path
      return myDescriptor.getMntRoot() + convertWindowsPath(windowsPath);
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

  /**
   * @param windowsAbsolutePath properly formatted windows local absolute path: {@code drive:\path}
   * @return windows path converted to the linux path according to wsl rules: {@code c:\some\path} => {@code c/some/path}
   */
  @NotNull
  static String convertWindowsPath(@NotNull String windowsAbsolutePath) {
    return Character.toLowerCase(windowsAbsolutePath.charAt(0)) + FileUtil.toSystemIndependentName(windowsAbsolutePath.substring(2));
  }

  /**
   * @return stringified command line, passed to the {@link #patchCommandLine(GeneralCommandLine, Project, String, boolean)}
   */
  @Contract("null->null")
  @Nullable
  public static String getOriginalCommandLineText(@Nullable GeneralCommandLine commandLine) {
    return commandLine == null ? null : ORIGINAL_COMMAND_LINE.get(commandLine);
  }

  /**
   * @return wrapper script created by the {@link #patchCommandLine(GeneralCommandLine, Project, String, boolean)}
   */
  @Contract("null->null")
  @Nullable
  public static String getWrapperScript(@Nullable GeneralCommandLine commandLine) {
    return commandLine == null ? null : WRAPPER_SCRIPT_TEXT.get(commandLine);
  }
}
