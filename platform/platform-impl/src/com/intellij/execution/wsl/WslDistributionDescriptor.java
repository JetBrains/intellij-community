// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static com.intellij.execution.wsl.WSLUtil.LOG;

/**
 * Data class describing a WSL distribution.
 * @apiNote Uniqueness of the descriptor defined by {@code id} only. All other fields may not be unique.
 */
@Tag("descriptor")
final class WslDistributionDescriptor {
  @Tag("id")
  private String myId;
  @Tag("microsoft-id")
  private String myMsId;
  /**
   * Absolute or relative executable path. Relative path resolved from default WSL executables root.
   */
  @Tag("executable-path")
  private String myExecutablePath;
  @Tag("presentable-name")
  private String myPresentableName;

  private final AtomicNotNullLazyValue<String> myMntRootProvider = AtomicNotNullLazyValue.createValue(this::computeMntRoot);

  /**
   * Necessary for serializer
   */
  WslDistributionDescriptor() {
  }

  WslDistributionDescriptor(@NotNull String id,
                                   @NotNull String msId,
                                   @NotNull String executablePath,
                                   @NotNull String presentableName) {
    myId = id;
    myMsId = msId;
    myExecutablePath = executablePath;
    this.myPresentableName = presentableName;
  }

  @NotNull
  public String getId() {
    return Objects.requireNonNull(myId);
  }

  @NotNull
  public String getMsId() {
    return Objects.requireNonNull(myMsId);
  }

  @NotNull
  public String getExecutablePath() {
    return Objects.requireNonNull(myExecutablePath);
  }

  @NotNull
  public String getPresentableName() {
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
    return "WslDistributionDescriptor{" +
           "id='" + myId + '\'' +
           ", msId='" + myMsId + '\'' +
           '}';
  }

  /**
   * @return the mount point for current distribution. Default value of {@code /mnt/} may be overriden with {@code /etc/wsl.conf}
   * @apiNote caches value per IDE run. Meaning - reconfiguring of this option in WSL requires IDE restart.
   */
  @NotNull
  final String getMntRoot() {
    return myMntRootProvider.getValue();
  }

  /**
   * @see #getMntRoot()
   */
  @NotNull
  private String computeMntRoot() {
    String windowsCurrentDirectory = System.getProperty("user.dir");

    if (StringUtil.isEmpty(windowsCurrentDirectory) || windowsCurrentDirectory.length() < 3) {
      LOG.warn("Could not obtain current directory from user.dir (or path is too short): " + windowsCurrentDirectory);
      return WSLDistribution.DEFAULT_WSL_MNT_ROOT;
    }

    WSLDistribution distribution = WSLUtil.getDistributionById(getId());
    if (distribution == null) {
      return WSLDistribution.DEFAULT_WSL_MNT_ROOT;
    }
    ProcessOutput pwdOutput;
    try {
      pwdOutput = distribution.executeOnWsl(-1, "pwd");
    }
    catch (ExecutionException e) {
      LOG.warn("Error reading pwd output for " + getId(), e);
      return WSLDistribution.DEFAULT_WSL_MNT_ROOT;
    }

    if (pwdOutput.getExitCode() != 0) {
      LOG.info("Non-zero exit code while fetching pwd: " +
               "[id=" + getId() + "; " +
               "[exitCode=" + pwdOutput.getExitCode() + "; " +
               "[stderr=" + pwdOutput.getStderr() + "; " +
               "[stdout=" + pwdOutput.getStdout() + "]");
      return WSLDistribution.DEFAULT_WSL_MNT_ROOT;
    }

    List<String> pwdOutputLines = pwdOutput.getStdoutLines();

    if (pwdOutputLines.size() != 1) {
      LOG.warn("One line response expected from `pwd`: " +
               "[id=" + getId() + "; " +
               "exitCode=" + pwdOutput.getExitCode() + "; " +
               "stderr=" + pwdOutput.getStderr() + "; " +
               "stdout=" + pwdOutput.getStdout() + "]");
      return WSLDistribution.DEFAULT_WSL_MNT_ROOT;
    }

    String wslCurrentDirectory = pwdOutputLines.get(0).trim();

    String currentPathSuffix = WSLDistribution.convertWindowsPath(windowsCurrentDirectory);
    if (StringUtil.endsWithIgnoreCase(wslCurrentDirectory, currentPathSuffix)) {
      return StringUtil.trimEnd(wslCurrentDirectory, currentPathSuffix, true);
    }
    LOG.warn("Wsl current directory does not ends with windows converted suffix: " +
             "[pwd=" + wslCurrentDirectory + "; " +
             "suffix=" + currentPathSuffix + "]");
    return WSLDistribution.DEFAULT_WSL_MNT_ROOT;
  }
}
