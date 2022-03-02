// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.execution.wsl.WSLUtil.LOG;

/**
 * Data class describing a WSL distribution.
 * @apiNote Uniqueness of the descriptor defined by {@code id} only. All other fields may not be unique.
 */
@Tag("descriptor")
final class WslDistributionDescriptor {
  private static final int PROBE_TIMEOUT = SystemProperties.getIntProperty("ide.wsl.probe.timeout", 60_000);

  @Tag("id")
  private @NlsSafe String myId;
  @Tag("microsoft-id")
  private @NlsSafe String myMsId;
  /**
   * Absolute or relative executable path. Relative path resolved from default WSL executables root.
   */
  @Tag("executable-path")
  private @NlsSafe @Nullable String myExecutablePath;
  @Tag("presentable-name")
  private @NlsSafe String myPresentableName;

  private final ClearableLazyValue<String> myMntRootProvider =
    createAtomicClearableLazyValue(() -> executeOrRunTask(pi -> computeMntRoot(pi)));

  /**
   * Necessary for serializer
   */
  @SuppressWarnings("unused")
  WslDistributionDescriptor() { }

  WslDistributionDescriptor(@NotNull String msId) {
    this(msId, msId, null, msId);
  }

  WslDistributionDescriptor(@NotNull String msId,
                            @Nullable String executablePath,
                            @NotNull String presentableName) {
    this(msId, msId, executablePath, presentableName);
  }

  WslDistributionDescriptor(@NotNull String id,
                            @NotNull String msId,
                            @Nullable String executablePath,
                            @NotNull String presentableName) {
    myId = id;
    myMsId = msId;
    myExecutablePath = executablePath;
    this.myPresentableName = presentableName;
  }

  public @NotNull @NlsSafe String getId() {
    return Objects.requireNonNull(myId);
  }


  public @NotNull @NlsSafe String getMsId() {
    return Objects.requireNonNull(myMsId);
  }

  public @Nullable @NlsSafe String getExecutablePath() {
    return myExecutablePath;
  }

  public @NotNull @NlsSafe String getPresentableName() {
    return Objects.requireNonNull(myPresentableName);
  }

  /**
   * @return if fields are set as expected (non-empty). Required to check consistency after deserialization
   */
  boolean isValid() {
    return StringUtil.isNotEmpty(myId) &&
           StringUtil.isNotEmpty(myMsId) &&
           StringUtil.isNotEmpty(myExecutablePath) &&
           StringUtil.isNotEmpty(myPresentableName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    WslDistributionDescriptor that = (WslDistributionDescriptor)o;
    return Objects.equals(getId(), that.getId());
  }

  @Override
  public int hashCode() {
    return getId().hashCode();
  }

  @Override
  public String toString() {
    return "WslDistributionDescriptor{id='" + myId + "', msId='" + myMsId + "'}";
  }

  /**
   * @return the mount point for current distribution. Default value of {@code /mnt/} may be overridden with {@code /etc/wsl.conf}
   * @apiNote caches value per IDE run. Meaning - reconfiguring of this option in WSL requires IDE restart.
   */
  @NotNull @NlsSafe String getMntRoot() {
    return myMntRootProvider.getValue();
  }

  /**
   * @see #getMntRoot()
   */
  private @NotNull @NlsSafe String computeMntRoot(@Nullable ProgressIndicator pi) {
    String windowsCurrentDirectory = System.getProperty("user.dir");

    if (StringUtil.isEmpty(windowsCurrentDirectory) || windowsCurrentDirectory.length() < 3) {
      LOG.warn("Could not obtain current directory from user.dir (or path is too short): " + windowsCurrentDirectory);
      return WSLDistribution.DEFAULT_WSL_MNT_ROOT;
    }

    WSLCommandLineOptions options = new WSLCommandLineOptions().setLaunchWithWslExe(true).setExecuteCommandInShell(false);
    String wslCurrentDirectory = readWslOutputLine(options, List.of("pwd"), pi);
    if (wslCurrentDirectory == null) return WSLDistribution.DEFAULT_WSL_MNT_ROOT;

    String currentPathSuffix = WSLDistribution.convertWindowsPath(windowsCurrentDirectory);
    if (StringUtil.endsWithIgnoreCase(wslCurrentDirectory, currentPathSuffix)) {
      return StringUtil.trimEnd(wslCurrentDirectory, currentPathSuffix, true);
    }
    LOG.warn("Wsl current directory does not ends with windows converted suffix: " +
             "[pwd=" + wslCurrentDirectory + "; " +
             "suffix=" + currentPathSuffix + "]");
    return WSLDistribution.DEFAULT_WSL_MNT_ROOT;
  }

  private @Nullable String readWslOutputLine(WSLCommandLineOptions options, List<String> command, @Nullable ProgressIndicator pi) {
    List<String> pwdOutputLines = readWSLOutput(options, command, pi);
    if (pwdOutputLines == null) return null;
    if (pwdOutputLines.size() != 1) {
      LOG.warn("One line response expected: " +
               "[id=" + getId() + "; " +
               "stdout=" + pwdOutputLines + "]");
      return null;
    }

    return pwdOutputLines.get(0).trim();
  }

  private @Nullable List<String> readWSLOutput(WSLCommandLineOptions options, List<String> command, @Nullable ProgressIndicator pi) {
    WSLDistribution distribution = WslDistributionManager.getInstance().getOrCreateDistributionByMsId(getId());

    final ProcessOutput output;
    try {
      var commandLine = distribution.patchCommandLine(new GeneralCommandLine(command), null, options);
      var processHandler = new CapturingProcessHandler(commandLine);
      output = pi == null ? processHandler.runProcess(PROBE_TIMEOUT) : processHandler.runProcessWithProgressIndicator(pi, PROBE_TIMEOUT);
    }
    catch (ExecutionException e) {
      LOG.warn("Start failed on " + getId(), e);
      return null;
    }

    if (output.getExitCode() != 0) {
      LOG.info("Execution failed on " + getId() +
               " [exitCode=" + output.getExitCode() +
               "; stderr=" + output.getStderr() +
               "; stdout=" + output.getStdout() + "]");
      return null;
    }

    return output.getStdoutLines();
  }

  private static <T> T executeOrRunTask(@NotNull Function<@Nullable ProgressIndicator, @NotNull ? extends T> commandRunner) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      return commandRunner.apply(null);
    }
    return ProgressManager.getInstance().run(new Task.WithResult<>(null, IdeBundle.message("wsl.executing.process"), true) {
      @Override
      protected T compute(@NotNull ProgressIndicator indicator) throws RuntimeException {
        return commandRunner.apply(indicator);
      }
    });
  }

  public static @NotNull <T> ClearableLazyValue<T> createAtomicClearableLazyValue(@NotNull Supplier<? extends T> computable) {
    return new ClearableLazyValue<>() {
      private long myExternalChangesCount = getCurrentExternalChangesCount();

      @Override
      protected @NotNull T compute() {
        return computable.get();
      }

      @Override
      public synchronized @NotNull T getValue() {
        final long curExternalChangesCount = getCurrentExternalChangesCount();
        if (curExternalChangesCount != myExternalChangesCount) {
          myExternalChangesCount = curExternalChangesCount;
          drop(); // drop cache
        }
        return super.getValue();
      }

      private long getCurrentExternalChangesCount() {
        return SaveAndSyncHandler.getInstance().getExternalChangesTracker().getModificationCount();
      }
    };
  }
}
