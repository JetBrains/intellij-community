// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.google.common.net.InetAddresses;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialPromptDialog;
import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.*;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.intellij.execution.wsl.WSLUtil.LOG;

/**
 * Represents a single linux distribution in WSL, installed after <a href="https://blogs.msdn.microsoft.com/commandline/2017/10/11/whats-new-in-wsl-in-windows-10-fall-creators-update/">Fall Creators Update</a>
 *
 * @see WSLUtil
 */
public class WSLDistribution {
  public static final String DEFAULT_WSL_MNT_ROOT = "/mnt/";
  private static final int RESOLVE_SYMLINK_TIMEOUT = 10000;
  private static final String RUN_PARAMETER = "run";
  public static final String UNC_PREFIX = "\\\\wsl$\\";
  private static final String WSLENV = "WSLENV";
  private static final int DEFAULT_TIMEOUT = SystemProperties.getIntProperty("ide.wsl.probe.timeout", 20_000);

  private static final Key<ProcessListener> SUDO_LISTENER_KEY = Key.create("WSL sudo listener");

  private final @NotNull WslDistributionDescriptor myDescriptor;
  private final @Nullable Path myExecutablePath;
  private final NullableLazyValue<String> myHostIp = NullableLazyValue.createValue(this::readHostIp);
  private final NullableLazyValue<String> myWslIp = NullableLazyValue.createValue(this::readWslIp);
  private final NullableLazyValue<String> myShellPath = NullableLazyValue.createValue(this::readShellPath);
  private final NullableLazyValue<String> myUserHomeProvider = NullableLazyValue.createValue(this::readUserHome);

  protected WSLDistribution(@NotNull WSLDistribution dist) {
    this(dist.myDescriptor, dist.myExecutablePath);
  }

  WSLDistribution(@NotNull WslDistributionDescriptor descriptor, @Nullable Path executablePath) {
    myDescriptor = descriptor;
    myExecutablePath = executablePath;
  }

  public WSLDistribution(@NotNull String msId) {
    this(new WslDistributionDescriptor(msId), null);
  }

  /**
   * @deprecated please don't use it, to be removed
   * @return executable file, null for WSL distributions parsed from `wsl.exe --list` output
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Deprecated
  public @Nullable Path getExecutablePath() {
    return myExecutablePath;
  }

  /**
   * @return identification data of WSL distribution.
   */
  public @Nullable @NlsSafe String readReleaseInfo() {
    try {
      final String key = "PRETTY_NAME";
      final String releaseInfo = "/etc/os-release"; // available for all distributions
      final ProcessOutput output = executeOnWsl(10000, "cat", releaseInfo);
      if (LOG.isDebugEnabled()) LOG.debug("Reading release info: " + getId());
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
   * @return creates and patches command line, e.g:
   * {@code ruby -v} => {@code bash -c "ruby -v"}
   */
  public @NotNull GeneralCommandLine createWslCommandLine(String @NotNull ... command) throws ExecutionException {
    return patchCommandLine(new GeneralCommandLine(command), null, new WSLCommandLineOptions());
  }

  /**
   * Creates a patched command line, executes it on wsl distribution and returns output
   *
   * @param command                linux command, eg {@code gem env}
   * @param options                {@link WSLCommandLineOptions} instance
   * @param timeout                timeout in ms
   * @param processHandlerConsumer consumes process handler just before execution, may be used for cancellation
   */
  public @NotNull ProcessOutput executeOnWsl(@NotNull List<String> command,
                                             @NotNull WSLCommandLineOptions options,
                                             int timeout,
                                             @Nullable Consumer<? super ProcessHandler> processHandlerConsumer) throws ExecutionException {
    GeneralCommandLine commandLine = patchCommandLine(new GeneralCommandLine(command), null, options);
    CapturingProcessHandler processHandler = new CapturingProcessHandler(commandLine);
    if (processHandlerConsumer != null) {
      processHandlerConsumer.consume(processHandler);
    }
    return processHandler.runProcess(timeout);
  }

  public @NotNull ProcessOutput executeOnWsl(int timeout, @NonNls String @NotNull ... command) throws ExecutionException {
    return executeOnWsl(Arrays.asList(command), new WSLCommandLineOptions(), timeout, null);
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
      throw new ExecutionException(IdeBundle.message("wsl.rsync.unable.to.copy.files.dialog.message", windowsPath));
    }
    command.add(targetWslPath + "/");
    return executeOnWsl(command, new WSLCommandLineOptions(), -1, handlerConsumer);
  }

  /**
   * @deprecated use {@link #patchCommandLine(GeneralCommandLine, Project, WSLCommandLineOptions)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public @NotNull <T extends GeneralCommandLine> T patchCommandLine(@NotNull T commandLine,
                                                                    @Nullable Project project,
                                                                    @Nullable String remoteWorkingDir,
                                                                    boolean askForSudo) {
    WSLCommandLineOptions options = new WSLCommandLineOptions()
      .setRemoteWorkingDirectory(remoteWorkingDir)
      .setSudo(askForSudo);
    try {
      return patchCommandLine(commandLine, project, options);
    }
    catch (ExecutionException e) {
      throw new IllegalStateException("Cannot patch command line for WSL", e);
    }
  }

  /**
   * Patches passed command line to make it runnable in WSL context, e.g changes {@code date} to {@code ubuntu run "date"}.<p/>
   * <p>
   * Environment variables and working directory are mapped to the chain calls: working dir using {@code cd} and environment variables using {@code export},
   * e.g {@code bash -c "export var1=val1 && export var2=val2 && cd /some/working/dir && date"}.<p/>
   * <p>
   * Method should properly handle quotation and escaping of the environment variables.<p/>
   *
   * @param commandLine command line to patch
   * @param project     current project
   * @param options     {@link WSLCommandLineOptions} instance
   * @param <T>         GeneralCommandLine or descendant
   * @return original {@code commandLine}, prepared to run in WSL context
   */
  public @NotNull <T extends GeneralCommandLine> T patchCommandLine(@NotNull T commandLine,
                                                                    @Nullable Project project,
                                                                    @NotNull WSLCommandLineOptions options) throws ExecutionException {
    logCommandLineBefore(commandLine, options);
    Path executable = getExecutablePath();
    boolean launchWithWslExe = options.isLaunchWithWslExe() || executable == null;
    Path wslExe = launchWithWslExe ? findWslExe() : null;
    if (wslExe == null && executable == null) {
      throw new ExecutionException(IdeBundle.message("wsl.not.installed.dialog.message"));
    }
    boolean executeCommandInShell = wslExe == null || options.isExecuteCommandInShell();
    List<String> linuxCommand = buildLinuxCommand(commandLine, executeCommandInShell);

    if (options.isSudo()) { // fixme shouldn't we sudo for every chunk? also, preserve-env, login?
      prependCommand(linuxCommand, "sudo", "-S", "-p", "''");
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
            IdeBundle.message("wsl.enter.root.password.dialog.title"),
            IdeBundle.message("wsl.sudo.password.for.root.label", getPresentableName()),
            new CredentialAttributes("WSL", "root", WSLDistribution.class),
            true
          );
          if (password != null) {
            try (PrintWriter pw = new PrintWriter(input, false, commandLine.getCharset())) {
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

    if (executeCommandInShell && StringUtil.isNotEmpty(options.getRemoteWorkingDirectory())) {
      prependCommand(linuxCommand, "cd", CommandLineUtil.posixQuote(options.getRemoteWorkingDirectory()), "&&");
    }
    if (executeCommandInShell && !options.isPassEnvVarsUsingInterop()) {
      commandLine.getEnvironment().forEach((key, val) -> {
        prependCommand(linuxCommand, "export", CommandLineUtil.posixQuote(key) + "=" + CommandLineUtil.posixQuote(val), "&&");
      });
      commandLine.getEnvironment().clear();
    }
    else {
      passEnvironmentUsingInterop(commandLine);
    }
    if (executeCommandInShell) {
      for (String command : options.getInitShellCommands()) {
        prependCommand(linuxCommand, command, "&&");
      }
    }

    commandLine.getParametersList().clearAll();
    String linuxCommandStr = StringUtil.join(linuxCommand, " ");
    if (wslExe != null) {
      commandLine.setExePath(wslExe.toString());
      commandLine.addParameters("--distribution", getMsId());
      if (options.isExecuteCommandInShell()) {
        if (options.isExecuteCommandInDefaultShell()) {
          commandLine.addParameters("$SHELL", "-c", linuxCommandStr);
        }
        else {
          commandLine.addParameters("--exec", options.getShellPath());
          if (options.isExecuteCommandInInteractiveShell()) {
            commandLine.addParameters("-i");
          }
          if (options.isExecuteCommandInLoginShell()) {
            commandLine.addParameters("-l");
          }
          commandLine.addParameters("-c", linuxCommandStr);
        }
      }
      else {
        commandLine.addParameter("--exec");
        commandLine.addParameters(linuxCommand);
      }
    }
    else {
      commandLine.setExePath(executable.toString());
      commandLine.addParameter(getRunCommandLineParameter());
      commandLine.addParameter(linuxCommandStr);
    }

    logCommandLineAfter(commandLine);
    return commandLine;
  }

  private void logCommandLineBefore(@NotNull GeneralCommandLine commandLine, @NotNull WSLCommandLineOptions options) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("[" + getId() + "] " +
                "Patching: " +
                commandLine.getCommandLineString() +
                "; options: " +
                options +
                "; envs: " + commandLine.getEnvironment()
      );
    }
  }

  private void logCommandLineAfter(@NotNull GeneralCommandLine commandLine) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("[" + getId() + "] " + "Patched as: " + commandLine.getCommandLineList(null));
    }
  }

  public static @Nullable Path findWslExe() {
    File file = PathEnvironmentVariableUtil.findInPath("wsl.exe");
    return file != null ? file.toPath() : null;
  }

  private static @NotNull List<String> buildLinuxCommand(@NotNull GeneralCommandLine commandLine, boolean executeCommandInShell) {
    List<String> command = ContainerUtil.concat(List.of(commandLine.getExePath()), commandLine.getParametersList().getList());
    return new ArrayList<>(ContainerUtil.map(command, executeCommandInShell ? CommandLineUtil::posixQuote : Functions.identity()));
  }

  // https://blogs.msdn.microsoft.com/commandline/2017/12/22/share-environment-vars-between-wsl-and-windows/
  private static void passEnvironmentUsingInterop(@NotNull GeneralCommandLine commandLine) {
    StringBuilder builder = new StringBuilder();
    for (String envName : commandLine.getEnvironment().keySet()) {
      if (StringUtil.isNotEmpty(envName)) {
        if (builder.length() > 0) {
          builder.append(":");
        }
        builder.append(envName).append("/u");
      }
    }
    if (builder.length() > 0) {
      String prevValue = commandLine.getEnvironment().get(WSLENV);
      if (prevValue == null) {
        prevValue = commandLine.getParentEnvironment().get(WSLENV);
      }
      String value = prevValue != null ? StringUtil.trimEnd(prevValue, ':') + ':' + builder
                                       : builder.toString();
      commandLine.getEnvironment().put(WSLENV, value);
    }
  }

  protected @NotNull @NlsSafe String getRunCommandLineParameter() {
    return RUN_PARAMETER;
  }

  /**
   * Attempts to resolve symlink with a given timeout
   *
   * @param path                  path in question
   * @param timeoutInMilliseconds timeout for execution
   * @return actual file name
   */
  public @NotNull @NlsSafe String resolveSymlink(@NotNull String path, int timeoutInMilliseconds) {

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

  public @NotNull @NlsSafe String resolveSymlink(@NotNull String path) {
    return resolveSymlink(path, RESOLVE_SYMLINK_TIMEOUT);
  }

  /**
   * Patches process handler with sudo listener, asking user for the password
   *
   * @param commandLine    patched command line
   * @param processHandler process handler, created from patched commandline
   * @return passed processHandler, patched with sudo listener if any
   */
  public @NotNull <T extends ProcessHandler> T patchProcessHandler(@NotNull GeneralCommandLine commandLine, @NotNull T processHandler) {
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
  public @NotNull Map<String, String> getEnvironment() {
    try {
      ProcessOutput processOutput =
        executeOnWsl(Collections.singletonList("env"),
                     new WSLCommandLineOptions()
                       .setExecuteCommandInShell(true)
                       .setExecuteCommandInLoginShell(true)
                       .setExecuteCommandInInteractiveShell(true),
                     5000,
                     null);
      Map<String, String> result = new HashMap<>();
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
   * @return Windows-dependent path for a file, pointed by {@code wslPath} in WSL, or {@code null} if path is unmappable
   */

  public @Nullable @NlsSafe String getWindowsPath(@NotNull String wslPath) {
    if (wslPath.startsWith(getMntRoot())) {
      return WSLUtil.getWindowsPath(wslPath, getMntRoot());
    }
    return getUNCRoot() + FileUtil.toSystemDependentName(wslPath);
  }

  /**
   * @return Linux path for a file pointed by {@code windowsPath} or null if unavailable, like \\MACHINE\path
   */
  public @Nullable @NlsSafe String getWslPath(@NotNull String windowsPath) {
    if (FileUtil.toSystemDependentName(windowsPath).startsWith(UNC_PREFIX)) {
      windowsPath = StringUtil.trimStart(FileUtil.toSystemDependentName(windowsPath), UNC_PREFIX);
      int index = windowsPath.indexOf('\\');
      if (index == -1) return null;

      String distName = windowsPath.substring(0, index);
      if (!distName.equalsIgnoreCase(myDescriptor.getMsId())) {
        throw new IllegalArgumentException("Trying to get WSL path from a different WSL distribution");
      }
      return FileUtil.toSystemIndependentName(windowsPath.substring(index));
    }

    //noinspection deprecation
    if (FileUtil.isWindowsAbsolutePath(windowsPath)) { // absolute windows path => /mnt/disk_letter/path
      return getMntRoot() + convertWindowsPath(windowsPath);
    }
    return null;
  }

  /**
   * @see WslDistributionDescriptor#getMntRoot()
   */
  public final @NotNull @NlsSafe String getMntRoot() {
    return myDescriptor.getMntRoot();
  }

  public final @Nullable @NlsSafe String getUserHome() {
    return myUserHomeProvider.getValue();
  }

  private @NlsSafe @Nullable String readUserHome() {
    return getEnvironmentVariable("HOME");
  }

  /**
   * @param windowsAbsolutePath properly formatted windows local absolute path: {@code drive:\path}
   * @return windows path converted to the linux path according to wsl rules: {@code c:\some\path} => {@code c/some/path}
   */
  static @NotNull @NlsSafe String convertWindowsPath(@NotNull String windowsAbsolutePath) {
    return Character.toLowerCase(windowsAbsolutePath.charAt(0)) + FileUtil.toSystemIndependentName(windowsAbsolutePath.substring(2));
  }

  public @NotNull @NlsSafe String getId() {
    return myDescriptor.getId();
  }

  public @NotNull @NlsSafe String getMsId() {
    return myDescriptor.getMsId();
  }

  public @NotNull @NlsSafe String getPresentableName() {
    return myDescriptor.getPresentableName();
  }

  @Override
  public String toString() {
    return "WSLDistribution{myDescriptor=" + myDescriptor + '}';
  }

  private static void prependCommand(@NotNull List<? super String> command, String @NotNull ... commandToPrepend) {
    command.addAll(0, Arrays.asList(commandToPrepend));
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o != null && getClass() == o.getClass() && getMsId().equals(((WSLDistribution)o).getMsId());
  }

  @Override
  public int hashCode() {
    return Strings.stringHashCodeInsensitive(getMsId());
  }

  /** @deprecated use {@link WSLDistribution#getUNCRootPath()} instead */
  @Deprecated
  public @NotNull File getUNCRoot() {
    return new File(UNC_PREFIX + myDescriptor.getMsId());
  }

  /**
   * @return UNC root for the distribution, e.g. {@code \\wsl$\Ubuntu}
   */
  @ApiStatus.Experimental
  public @NotNull Path getUNCRootPath() {
    return Paths.get(UNC_PREFIX + myDescriptor.getMsId());
  }

  /**
   * @return UNC root for the distribution, e.g. {@code \\wsl$\Ubuntu}
   * @implNote there is a hack in {@link LocalFileSystemBase#getAttributes(VirtualFile)} which causes all network
   * virtual files to exists all the time. So we need to check explicitly that root exists. After implementing proper non-blocking check
   * for the network resource availability, this method may be simplified to findFileByIoFile
   * @see VfsUtil#findFileByIoFile(File, boolean)
   */
  @ApiStatus.Experimental
  public @Nullable VirtualFile getUNCRootVirtualFile(boolean refreshIfNeed) {
    if (!Experiments.getInstance().isFeatureEnabled("wsl.p9.support")) {
      return null;
    }
    File uncRoot = getUNCRoot();
    return uncRoot.exists() ? VfsUtil.findFileByIoFile(uncRoot, refreshIfNeed) : null;
  }

  // https://docs.microsoft.com/en-us/windows/wsl/compare-versions#accessing-windows-networking-apps-from-linux-host-ip
  public String getHostIp() {
    return myHostIp.getValue();
  }

  public String getWslIp() {
    return myWslIp.getValue();
  }

  public InetAddress getHostIpAddress() {
    return InetAddresses.forString(getHostIp());
  }

  public InetAddress getWslIpAddress() {
    return InetAddresses.forString(getWslIp());
  }

  private @Nullable String readHostIp() {
    final String releaseInfo = "/etc/resolv.conf"; // available for all distributions
    final ProcessOutput output;
    try {
      output = executeOnWsl(List.of("cat", releaseInfo),
                            new WSLCommandLineOptions().setExecuteCommandInShell(false), 10_000, null);
    }
    catch (ExecutionException e) {
      LOG.info("Cannot read host ip", e);
      return null;
    }
    if (LOG.isDebugEnabled()) LOG.debug("Reading release info: " + getId());
    if (!output.checkSuccess(LOG)) return null;
    for (String line : output.getStdoutLines(true)) {
      if (line.startsWith("nameserver")) {
        return line.substring("nameserver".length()).trim();
      }
    }
    return null;
  }

  private @Nullable String readWslIp() {
    final ProcessOutput output;
    try {
      output = executeOnWsl(List.of("ip", "addr", "show", "eth0"),
                            new WSLCommandLineOptions().setExecuteCommandInShell(false), 10_000, null);
    }
    catch (ExecutionException e) {
      LOG.info("Cannot read wsl ip", e);
      return null;
    }
    if (LOG.isDebugEnabled()) LOG.debug("Reading eth0 info: " + getId());
    if (!output.checkSuccess(LOG)) return null;
    for (String line : output.getStdoutLines(true)) {
      String trimmed = line.trim();
      if (trimmed.startsWith("inet ")) {
        int index = trimmed.indexOf("/");
        if (index != -1) {
          return trimmed.substring("inet ".length(), index);
        }
      }
    }
    return null;
  }

  public @NonNls @Nullable String getEnvironmentVariable(String name) {
    WSLCommandLineOptions options = new WSLCommandLineOptions()
      .setExecuteCommandInInteractiveShell(true)
      .setExecuteCommandInLoginShell(true)
      .setShellPath(getShellPath());
    return executeInShellAndGetCommandOnlyStdout(List.of("printenv", name), options, DEFAULT_TIMEOUT, true);
  }

  public @NlsSafe @NotNull String getShellPath() {
    return ObjectUtils.notNull(myShellPath.getValue(), WSLCommandLineOptions.DEFAULT_SHELL);
  }

  private @NlsSafe @Nullable String readShellPath() {
    WSLCommandLineOptions options = new WSLCommandLineOptions().setExecuteCommandInDefaultShell(true);
    return executeInShellAndGetCommandOnlyStdout(List.of("printenv", "SHELL"), options, DEFAULT_TIMEOUT, true);
  }

  @SuppressWarnings("SameParameterValue")
  @Nullable String executeInShellAndGetCommandOnlyStdout(@NotNull List<String> command,
                                                         @NotNull WSLCommandLineOptions options,
                                                         int timeout,
                                                         boolean oneLineStdoutExpected) {
    options.setExecuteCommandInShell(true);
    // When command is executed in interactive/login shell, the result stdout may contain additional output
    // produced by shell configuration files, for example, "Message Of The Day".
    // Let's print some unique message before executing the command to know where command output begins in the result output.
    String prefixText = "intellij: executing command...";
    options.addInitCommand("echo " + CommandLineUtil.posixQuote(prefixText));
    try {
      ProcessOutput output = executeOnWsl(command, options, timeout, null);
      String stdout = output.getStdout();
      if (!output.isTimeout() && output.getExitCode() == 0) {
        String markerText = prefixText + LineSeparator.LF.getSeparatorString();
        int index = stdout.indexOf(markerText);
        if (index >= 0) {
          String commandOwnStdout = stdout.substring(index + markerText.length());
          return oneLineStdoutExpected ? expectOneLineOutput(command, commandOwnStdout) : commandOwnStdout;
        }
      }
      LOG.info("Failed to execute " + command + " for " + getMsId() + ": " +
               "exitCode=" + output.getExitCode() + ", timeout=" + output.isTimeout() +
               ", stdout=" + stdout + ", stderr=" + output.getStderr());
    }
    catch (ExecutionException e) {
      LOG.info("Failed to execute " + command + " for " + getMsId(), e);
    }
    return null;
  }

  private @NotNull String expectOneLineOutput(@NotNull List<String> command, @NotNull String stdout) {
    String converted = StringUtil.convertLineSeparators(stdout, LineSeparator.LF.getSeparatorString());
    List<String> lines = StringUtil.split(converted, LineSeparator.LF.getSeparatorString(), true, true);
    if (lines.size() != 1) {
      LOG.info("One line stdout expected: " + getMsId() + ", command=" + command + ", stdout=" + stdout + ", lines=" + lines.size());
    }
    return StringUtil.notNullize(ContainerUtil.getFirstItem(lines), stdout);
  }
}
