// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

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

  /**
   * Necessary for serializer
   */
  public WslDistributionDescriptor() {
  }

  public WslDistributionDescriptor(@NotNull String id,
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
}
