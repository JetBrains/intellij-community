// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.google.common.net.InetAddresses;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialPromptDialog;
import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.process.*;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.impl.wsl.WslConstants;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Functions;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.intellij.execution.wsl.WSLUtil.LOG;
import static com.intellij.util.ObjectUtils.coalesce;

/**
 * Represents a single linux distribution in WSL, installed after <a href="https://blogs.msdn.microsoft.com/commandline/2017/10/11/whats-new-in-wsl-in-windows-10-fall-creators-update/">Fall Creators Update</a>
 *
 * <h2>On network connectivity</h2>
 * WSL1 shares network stack and both sides may use ``127.0.0.1`` to connect to each other.
 * <br/>
 * On WSL2 both OSes have separate interfaces ("eth0" on Linux and "vEthernet (WSL)" on Windows).
 * One can't connect from Linux to Windows because of <a href="https://www.jetbrains.com/help/idea/how-to-use-wsl-development-environment-in-product.html#debugging_system_settings">Windows Firewall which you need to disable or accomplish with rule</a>, because
 * of that it is not recommended to connect to IJ from processes launched on WSL.
 * In other words, you shouldn't use {@link #getHostIpAddress()} in most cases.
 * <p>
 * If you cant avoid that, use {@link com.intellij.execution.wsl.WslProxy} which makes tunnel to solve firewall issues.
 * <br/>
 * Connecting from Windows to Linux is possible in most cases (see {@link #getWslIpAddress()} and in modern WSL2 you can even use
 * ``127.0.0.1`` if port is not occupied on Windows side.
 * VPNs might break eth0 connectivity on WSL side (see PY-59608). In this case, enable <code>wsl.proxy.connect.localhost</code>
 *
 * @see <a href="https://learn.microsoft.com/en-us/windows/wsl/networking">Microsoft guide</a>
 * @see WSLUtil
 */
public class WSLDistribution implements AbstractWslDistribution {
  public static final String DEFAULT_WSL_MNT_ROOT = "/mnt/";
  private static final String DEFAULT_WSL_IP = "127.0.0.1";
  private static final int RESOLVE_SYMLINK_TIMEOUT = 10000;
  private static final String RUN_PARAMETER = "run";
  static final String DEFAULT_SHELL = "/bin/sh";
  static final int DEFAULT_TIMEOUT = SystemProperties.getIntProperty("ide.wsl.probe.timeout", 20_000);
  private static final String SHELL_PARAMETER = "$SHELL";
  public static final String WSL_EXE = "wsl.exe";
  public static final String DISTRIBUTION_PARAMETER = "--distribution";
  public static final String EXIT_CODE_PARAMETER = "; exitcode=$?";
  public static final String EXEC_PARAMETER = "--exec";

  private static final Key<ProcessListener> SUDO_LISTENER_KEY = Key.create("WSL sudo listener");
  private static final Key<Boolean> NEVER_RUN_TTY_FIX = Key.create("Never run ttyfix");

  /**
   * @see <a href="https://www.gnu.org/software/bash/manual/html_node/Definitions.html#index-name">bash identifier definition</a>
   */
  static final Pattern ENV_VARIABLE_NAME_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

  private final @NotNull WslDistributionDescriptor myDescriptor;
  private final @Nullable Path myExecutablePath;
  private @Nullable Integer myVersion;

  private final WslDistributionSafeNullableLazyValue<IpOrException> myLazyHostIp = WslDistributionSafeNullableLazyValue.create(() -> {
    try {
      return new IpOrException(readHostIp());
    }
    catch (ExecutionException e) {
      return new IpOrException(e);
    }
  });
  private final WslDistributionSafeNullableLazyValue<String> myLazyWslIp = WslDistributionSafeNullableLazyValue.create(() -> {
    try {
      return readWslIp();
    }
    catch (ExecutionException ex) {
      // See class doc, IP section
      LOG.warn("Can't read WSL IP, will use default: " + DEFAULT_WSL_IP, ex);
      return DEFAULT_WSL_IP;
    }
  });
  private final WslDistributionSafeNullableLazyValue<String> myLazyShellPath =
    WslDistributionSafeNullableLazyValue.create(this::readShellPath);
  private final WslDistributionSafeNullableLazyValue<String> myLazyUserHome =
    WslDistributionSafeNullableLazyValue.create(this::readUserHome);

  protected WSLDistribution(@NotNull WSLDistribution dist) {
    this(dist.myDescriptor, dist.myExecutablePath);
    myVersion = dist.myVersion;
  }

  WSLDistribution(@NotNull WslDistributionDescriptor descriptor, @Nullable Path executablePath) {
    myDescriptor = descriptor;
    myExecutablePath = executablePath;
  }

  public WSLDistribution(@NotNull String msId) {
    this(new WslDistributionDescriptor(msId), null);
  }

  /**
   * @return executable file, null for WSL distributions parsed from `wsl.exe --list` output
   * @deprecated please don't use it, to be will be removed after we collect statistics and make sure versions before 1903 aren't used.
   * Check statistics and remove in the next version
   */
  @Deprecated(forRemoval = true)
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

  void setVersion(@Nullable Integer version) {
    myVersion = version;
  }

  /**
   * @return version if it can be determined or -1 instead
   */
  public int getVersion() {
    if (myVersion == null) {
      myVersion = WSLUtil.getWslVersion(this);
    }
    return myVersion;
  }

  /**
   * Creates a patched command line, executes it on wsl distribution and returns output
   *
   * @param command                linux command, eg {@code gem env}
   * @param options                {@link WSLCommandLineOptions} instance
   * @param timeout                timeout in ms
   * @param processHandlerConsumer consumes process handler just before execution, may be used for cancellation
   */
  @RequiresBackgroundThread(generateAssertion = false)
  public @NotNull ProcessOutput executeOnWsl(@NotNull List<String> command,
                                             @NotNull WSLCommandLineOptions options,
                                             int timeout,
                                             @Nullable Consumer<? super ProcessHandler> processHandlerConsumer) throws ExecutionException {
    GeneralCommandLine commandLine = patchCommandLine(new GeneralCommandLine(command), null, options);
    CapturingProcessHandler processHandler = new CapturingProcessHandler(commandLine);
    if (processHandlerConsumer != null) {
      processHandlerConsumer.consume(processHandler);
    }
    ProcessOutput output = processHandler.runProcess(timeout);
    if (output.getExitCode() != 0 || output.isTimeout() || output.isCancelled()) {
      LOG.info("command on wsl: " + commandLine.getCommandLineString() + " was failed:" +
               "ec=" + output.getExitCode() + ",timeout=" + output.isTimeout() + ",cancelled=" + output.isCancelled()
               + ",stderr=" + output.getStderr() + ",stdout=" + output.getStdout());
    }
    return output;
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public @NotNull ProcessOutput executeOnWsl(int timeout, @NonNls String @NotNull ... command) throws ExecutionException {
    return executeOnWsl(Arrays.asList(command), new WSLCommandLineOptions(), timeout, null);
  }

  /**
   * @deprecated use {@link #patchCommandLine(GeneralCommandLine, Project, WSLCommandLineOptions)} instead
   */
  @Deprecated
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

  @Override
  public @NotNull <T extends GeneralCommandLine> T patchCommandLine(@NotNull T commandLine,
                                                                    @Nullable Project project,
                                                                    @NotNull WSLCommandLineOptions options) throws ExecutionException {
    if (mustRunCommandLineWithIjent(options)) {
      LocalPtyOptions ptyOptions;
      if (commandLine instanceof PtyCommandLine ptyCommandLine) {
        ptyOptions = ptyCommandLine.getPtyOptions();
      }
      else {
        ptyOptions = null;
      }
      commandLine.setProcessCreator((processBuilder) -> {
        return WslIjentUtil.runProcessBlocking(WslIjentManager.getInstance(), project, this, processBuilder, options, ptyOptions);
      });
    }
    else {
      doPatchCommandLine(commandLine, project, options);
    }

    return commandLine;
  }

  @VisibleForTesting
  public static boolean mustRunCommandLineWithIjent(@NotNull WSLCommandLineOptions options) {
    return WslIjentManager.getInstance().isIjentAvailable() && !options.isLaunchWithWslExe();
  }

  final @NotNull <T extends GeneralCommandLine> T doPatchCommandLine(@NotNull T commandLine,
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

    final boolean isElevated = options.isSudo();
    // use old approach in case of wsl.exe is not available
    if (isElevated && wslExe == null) { // fixme shouldn't we sudo for every chunk? also, preserve-env, login?
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
            new CredentialAttributes("WSL", "root"),
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
      Comparator<Map.Entry<String, String>> comparator = Map.Entry.<String, String>comparingByKey().reversed();
      commandLine.getEnvironment().entrySet().stream().sorted(comparator).forEach((entry) -> {
        String key = entry.getKey();
        String val = entry.getValue();
        if (ENV_VARIABLE_NAME_PATTERN.matcher(key).matches()) {
          prependCommand(linuxCommand, "export", key + "=" + CommandLineUtil.posixQuote(val), "&&");
        }
        else {
          LOG.debug("Can not pass environment variable (bad name): '", key, "'");
        }
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
      commandLine.addParameters(DISTRIBUTION_PARAMETER, getMsId());
      if (isElevated) {
        commandLine.addParameters("-u", "root");
      }
      if (options.isExecuteCommandInShell()) {
        // workaround WSL1 problem: https://github.com/microsoft/WSL/issues/4082
        if (options.getSleepTimeoutSec() > 0 && getVersion() == 1) {
          linuxCommandStr += EXIT_CODE_PARAMETER + "; sleep " + options.getSleepTimeoutSec() + "; (exit $exitcode)";
        }

        if (options.isExecuteCommandInDefaultShell()) {
          fixTTYSize(commandLine);
          commandLine.addParameters(SHELL_PARAMETER, "-c", linuxCommandStr);
        }
        else {
          commandLine.addParameter(EXEC_PARAMETER);
          fixTTYSize(commandLine);
          commandLine.addParameter(getShellPath());
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
        commandLine.addParameter(EXEC_PARAMETER);
        commandLine.addParameters(linuxCommand);
      }
    }
    else {
      commandLine.setExePath(executable.toString());
      commandLine.addParameter(getRunCommandLineParameter());
      commandLine.addParameter(linuxCommandStr);
    }

    if (Registry.is("wsl.use.utf8.encoding", true)) {
      // Unlike the default system encoding on Windows (e.g. cp-1251),
      // UTF-8 is the default for most Linux distributions
      commandLine.setCharset(StandardCharsets.UTF_8);
    }

    logCommandLineAfter(commandLine);
    return commandLine;
  }


  /***
   * Never run {@link #fixTTYSize(GeneralCommandLine)}
   */
  @NotNull
  public static GeneralCommandLine neverRunTTYFix(@NotNull GeneralCommandLine commandLine) {
    commandLine.putUserData(NEVER_RUN_TTY_FIX, true);
    return commandLine;
  }

  /**
   * Workaround for <a href="https://github.com/microsoft/WSL/issues/10701">wrong tty size WSL problem</a>
   */
  private void fixTTYSize(@NotNull GeneralCommandLine cmd) {
    if (!WslDistributionDescriptor.isCalculatingMountRootCommand(cmd)
        && cmd.getUserData(NEVER_RUN_TTY_FIX) == null // ttyfix not disabled explicitly
        // Even though mount root is calculated with `options.isExecuteCommandInShell()=false`,
        // let's protect from possible infinite recursion.
        && !(cmd instanceof PtyCommandLine)  // PTY command line has correct tty size
        && Registry.is("wsl.fix.initial.tty.size.when.running.without.tty", true)) {
      var ttyfix = AbstractWslDistributionKt.getToolLinuxPath(this, "ttyfix");
      cmd.addParameter(ttyfix);
    }
  }

  private void logCommandLineBefore(@NotNull GeneralCommandLine commandLine, @NotNull WSLCommandLineOptions options) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("[" + getId() + "] " +
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

  private static @Nullable Path testOverriddenWslExe;

  @TestOnly
  public static void testOverriddenWslExe(@NotNull Path path, @NotNull Disposable disposable) {
    Disposer.register(disposable, () -> {
      testOverriddenWslExe = null;
    });
    testOverriddenWslExe = path;
  }

  public static @Nullable Path findWslExe() {
    if (testOverriddenWslExe != null) return testOverriddenWslExe;

    File file = PathEnvironmentVariableUtil.findInPath(WSL_EXE);
    return file != null ? file.toPath() : null;
  }

  private static @NotNull List<String> buildLinuxCommand(@NotNull GeneralCommandLine commandLine, boolean executeCommandInShell) {
    List<String> command = ContainerUtil.concat(List.of(commandLine.getExePath()), commandLine.getParametersList().getList());
    return new ArrayList<>(ContainerUtil.map(command, executeCommandInShell ? CommandLineUtil::posixQuote : Functions.identity()));
  }

  // https://blogs.msdn.microsoft.com/commandline/2017/12/22/share-environment-vars-between-wsl-and-windows/
  private static void passEnvironmentUsingInterop(@NotNull GeneralCommandLine commandLine) {
    StringBuilder builder = new StringBuilder();
    commandLine.getEnvironment().keySet().stream().sorted().forEach((envName) -> {
      if (StringUtil.isNotEmpty(envName)) {
        if (!builder.isEmpty()) {
          builder.append(":");
        }
        builder.append(envName).append("/u");
      }
    });
    if (!builder.isEmpty()) {
      String prevValue = commandLine.getEnvironment().get(WslConstants.WSLENV);
      if (prevValue == null) {
        prevValue = commandLine.getParentEnvironment().get(WslConstants.WSLENV);
      }
      String value = prevValue != null ? StringUtil.trimEnd(prevValue, ':') + ':' + builder
                                       : builder.toString();
      commandLine.getEnvironment().put(WslConstants.WSLENV, value);
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
  public @Nullable Map<String, String> getEnvironment() {
    if (WslIjentManager.getInstance().isIjentAvailable()) {
      return WslIjentUtil.fetchLoginShellEnv(WslIjentManager.getInstance(), this, null, false);
    }
    try {
      ProcessOutput processOutput = WslExecution.executeInShellAndGetCommandOnlyStdout(
        this, new GeneralCommandLine("env"),
        new WSLCommandLineOptions()
          .setExecuteCommandInShell(true)
          .setExecuteCommandInLoginShell(true)
          .setExecuteCommandInInteractiveShell(true),
        DEFAULT_TIMEOUT);
      if (processOutput.getExitCode() == 0) {
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
    }
    catch (ExecutionException e) {
      LOG.warn(e);
    }
    return null;
  }

  public @NotNull @NlsSafe String getWindowsPath(@NotNull String wslPath) {
    return getWindowsPath(wslPath, this::getMntRoot);
  }

  /**
   * @return Windows-dependent path for a file, pointed by {@code wslPath} in WSL, or {@code null} if path is unmappable
   */
  public @NotNull @NlsSafe String getWindowsPath(@NotNull String wslPath, @NotNull Supplier<String> mntRootSupplier) {
    if (containsDriveLetter(wslPath)) {
      String windowsPath = WSLUtil.getWindowsPath(wslPath, mntRootSupplier.get());
      if (windowsPath != null) {
        return windowsPath;
      }
    }
    return getUNCRootPathString() + FileUtil.toSystemDependentName(FileUtil.normalize(wslPath));
  }

  private static boolean containsDriveLetter(@NotNull String linuxPath) {
    int slashInd = linuxPath.indexOf('/');
    while (slashInd >= 0) {
      int nextSlashInd = linuxPath.indexOf('/', slashInd + 1);
      if ((nextSlashInd == slashInd + 2 || (nextSlashInd == -1 && slashInd + 2 == linuxPath.length())) &&
          OSAgnosticPathUtil.isDriveLetter(linuxPath.charAt(slashInd + 1))) {
        return true;
      }
      slashInd = nextSlashInd;
    }
    return false;
  }

  /**
   * @deprecated Use {@link #getWslPath(Path)}
   */
  @Deprecated
  public final @Nullable String getWslPath(@NotNull @NlsSafe String windowsPath) {
    try {
      return getWslPath(Path.of(windowsPath));
    }
    catch (InvalidPathException e) {
      LOG.warn("Failed to convert '" + windowsPath + "' to wsl path", e);
      return null;
    }
  }

  @Override
  public @Nullable @NlsSafe String getWslPath(@NotNull Path windowsPath) {
    WslPath wslPath = WslPath.parseWindowsUncPath(windowsPath.toString());
    if (wslPath != null) {
      if (wslPath.getDistributionId().equalsIgnoreCase(myDescriptor.getMsId())) {
        return wslPath.getLinuxPath();
      }
      throw new IllegalArgumentException("Trying to get WSL path from a different WSL distribution. Requested path (" + windowsPath + ")" +
                                         " belongs to " + wslPath.getDistributionId() + " distribution" +
                                         ", but context distribution is " + myDescriptor.getMsId());
    }

    if (OSAgnosticPathUtil.isAbsoluteDosPath(windowsPath.toString())) { // absolute windows path => /mnt/disk_letter/path
      return getMntRoot() + convertWindowsPath(windowsPath.toString());
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
    return getValueWithLogging(myLazyUserHome, "user's home path");
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

  @Override
  public @NotNull @NlsSafe String getMsId() {
    return myDescriptor.getMsId();
  }

  public @NotNull @NlsSafe String getPresentableName() {
    return myDescriptor.getPresentableName();
  }

  @Override
  public String toString() {
    return myDescriptor.getMsId();
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

  /**
   * @return UNC root for the distribution, e.g. {@code \\wsl$\Ubuntu}
   */
  @Override
  @ApiStatus.Experimental
  public @NotNull Path getUNCRootPath() {
    return Path.of(getUNCRootPathString());
  }

  private @NotNull String getUNCRootPathString() {
    return WSLUtil.getUncPrefix() + myDescriptor.getMsId();
  }

  /**
   * Windows IP address. See class doc before using it, because this is probably not what you are looking for.
   *
   * @throws ExecutionException if IP can't be obtained (see logs for more info)
   * @deprecated use {@link com.intellij.execution.wsl.WslProxy} because Windows IP address is almost always closed by firewall and this method also uses `eth0` address which also might be broken
   */
  @Deprecated
  public final @NotNull InetAddress getHostIpAddress() throws ExecutionException {
    final var hostIpOrException = getValueWithLogging(myLazyHostIp, "host IP");
    if (hostIpOrException == null) {
      throw new ExecutionException(IdeBundle.message("wsl.error.host.ip.not.obtained.message", getPresentableName()));
    }
    return InetAddresses.forString(hostIpOrException.getIp());
  }

  /**
   * Linux IP address. See class doc IP section for more info.
   */
  public final @NotNull InetAddress getWslIpAddress() {
    if (Registry.is("wsl.proxy.connect.localhost")) {
      return InetAddresses.forString(DEFAULT_WSL_IP);
    }
    return InetAddresses.forString(coalesce(getValueWithLogging(myLazyWslIp, "WSL IP"), DEFAULT_WSL_IP));
  }

  // https://docs.microsoft.com/en-us/windows/wsl/compare-versions#accessing-windows-networking-apps-from-linux-host-ip
  private @NotNull String readHostIp() throws ExecutionException {
    String wsl1LoopbackAddress = getWsl1LoopbackAddress();
    if (wsl1LoopbackAddress != null) {
      return wsl1LoopbackAddress;
    }
    if (Registry.is("wsl.obtain.windows.host.ip.alternatively", true)) {
      // Connect to any port on WSL IP. The destination endpoint is not needed to be reachable as no real connection is established.
      // This transfers the socket into "connected" state including setting the local endpoint according to the system's routing table.
      // Works on Windows and Linux.
      try (DatagramSocket datagramSocket = new DatagramSocket()) {
        // We always need eth0 ip, can't use 127.0.0.1
        InetAddress wslAddr = InetAddresses.forString(readWslIp());
        // Any port in range [1, 0xFFFF] can be used. Port=0 is forbidden: https://datatracker.ietf.org/doc/html/rfc8085
        // "A UDP receiver SHOULD NOT bind to port zero".
        // Java asserts "port != 0" since v15 (https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8240533).
        int anyPort = 1;
        datagramSocket.connect(wslAddr, anyPort);
        return datagramSocket.getLocalAddress().getHostAddress();
      }
      catch (Exception e) {
        LOG.error("Cannot obtain Windows host IP alternatively: failed to connect to WSL IP. Fallback to default way.", e);
      }
    }

    return executeAndParseOutput(IdeBundle.message("wsl.win.ip"), strings -> {
      for (String line : strings) {
        if (line.startsWith("nameserver")) {
          return line.substring("nameserver".length()).trim();
        }
      }
      return null;
    }, "cat", "/etc/resolv.conf");
  }

  private @NotNull String readWslIp() throws ExecutionException {
    String wsl1LoopbackAddress = getWsl1LoopbackAddress();
    if (wsl1LoopbackAddress != null) {
      return wsl1LoopbackAddress;
    }

    return executeAndParseOutput(IdeBundle.message("wsl.wsl.ip"), strings -> {
      for (String line : strings) {
        String trimmed = line.trim();
        if (trimmed.startsWith("inet ")) {
          int index = trimmed.indexOf("/");
          if (index != -1) {
            return trimmed.substring("inet ".length(), index);
          }
        }
      }
      return null;
    }, "ip", "addr", "show", "eth0");
  }

  /**
   * Run command on WSL and parse IP from it
   *
   * @param ipType  WSL or Windows
   * @param parser  block that accepts stdout and parses IP from it
   * @param command command to run on WSL
   * @return IP
   * @throws ExecutionException IP can't be parsed
   */
  private @NotNull String executeAndParseOutput(@NlsContexts.DialogMessage @NotNull String ipType,
                                                @NotNull Function<List<@NlsSafe String>, @Nullable String> parser,
                                                @NotNull String @NotNull ... command)
    throws ExecutionException {
    final ProcessOutput output;
    output = executeOnWsl(Arrays.asList(command), new WSLCommandLineOptions(), 10_000, null);
    if (LOG.isDebugEnabled()) LOG.debug(ipType + " " + getId());
    if (!output.checkSuccess(LOG)) {
      LOG.warn(String.format("%s. Exit code: %s. Error %s", ipType, output.getExitCode(), output.getStderr()));
      throw new ExecutionException(IdeBundle.message("wsl.cant.parse.ip.no.output", ipType));
    }
    var stdout = output.getStdoutLines(true);
    var ip = parser.apply(stdout);
    if (ip != null) {
      return ip;
    }
    LOG.warn(String.format("Can't parse data for %s, stdout is %s", ipType, String.join("\n", stdout)));
    throw new ExecutionException(IdeBundle.message("wsl.cant.parse.ip.process.failed", ipType));
  }

  private @Nullable String getWsl1LoopbackAddress() {
    return getVersion() == 1 ? InetAddress.getLoopbackAddress().getHostAddress() : null;
  }

  public @NonNls @Nullable String getEnvironmentVariable(String name) {
    if (WslIjentAvailabilityService.getInstance().runWslCommandsViaIjent()) {
      Map<String, String> map = WslIjentUtil.fetchLoginShellEnv(WslIjentManager.getInstance(), this, null, false);
      return map.get(name);
    }
    WSLCommandLineOptions options = new WSLCommandLineOptions()
      .setExecuteCommandInInteractiveShell(true)
      .setExecuteCommandInLoginShell(true);
    return WslExecution.executeInShellAndGetCommandOnlyStdout(this, new GeneralCommandLine("printenv", name),
                                                              options.setLaunchWithWslExe(true), DEFAULT_TIMEOUT,
                                                              true);
  }

  public @NlsSafe @NotNull String getShellPath() {
    return coalesce(getValueWithLogging(myLazyShellPath, "user's shell path"), DEFAULT_SHELL);
  }

  @VisibleForTesting
  protected @Nullable String testOverriddenShellPath;

  private @NlsSafe @Nullable String readShellPath() {
    if (testOverriddenShellPath != null) return testOverriddenShellPath;

    WSLCommandLineOptions options = new WSLCommandLineOptions().setExecuteCommandInDefaultShell(true).setLaunchWithWslExe(true);
    return WslExecution.executeInShellAndGetCommandOnlyStdout(this, new GeneralCommandLine("printenv", "SHELL"), options, DEFAULT_TIMEOUT,
                                                              true);
  }

  private <T> @Nullable T getValueWithLogging(final @NotNull WslDistributionSafeNullableLazyValue<T> lazyValue,
                                              final @NotNull String fieldName) {
    final T value = lazyValue.getValue();

    if (value == null) {
      final var app = ApplicationManager.getApplication();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Lazy value for %s returned null: MsId=%s, EDT=%s, RA=%s, stackTrace=%s"
                    .formatted(fieldName, getMsId(), app.isDispatchThread(), app.isReadAccessAllowed(), ExceptionUtil.currentStackTrace()));
      }
      else {
        LOG.warn("Lazy value for %s returned null: MsId=%s, EDT=%s, RA=%s"
                   .formatted(fieldName, getMsId(), app.isDispatchThread(), app.isReadAccessAllowed()));
      }
    }

    return value;
  }
}
