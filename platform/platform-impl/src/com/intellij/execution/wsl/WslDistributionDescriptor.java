// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Data class describing a WSL distribution
 */
@Tag("descriptor")
class WslDistributionDescriptor {
  @Tag("jetbrains-id")
  private String id;
  @Tag("microsoft-id")
  private String msId;
  @Tag("executable-name")
  private String exeName;
  @Tag("presentable-name")
  private String presentableName;

  /**
   * Necessary for serializer
   */
  public WslDistributionDescriptor() {
  }

  public WslDistributionDescriptor(@NotNull String id, @NotNull String msId, @NotNull String exeName, @NotNull String presentableName) {
    this.id = id;
    this.msId = msId;
    this.exeName = exeName;
    this.presentableName = presentableName;
  }

  @NotNull
  public String getId() {
    return Objects.requireNonNull(id);
  }

  @NotNull
  public String getMsId() {
    return Objects.requireNonNull(msId);
  }

  @NotNull
  public String getExeName() {
    return Objects.requireNonNull(exeName);
  }

  @NotNull
  public String getPresentableName() {
    return Objects.requireNonNull(presentableName);
  }
}
