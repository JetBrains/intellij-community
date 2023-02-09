// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.local;

public final class LocalTargetType {
  /**
   * This value is used to express explicitly selected "local machine" target for Run Configuration.
   *
   * @see com.intellij.execution.target.TargetEnvironmentAwareRunProfile#getDefaultTargetName()
   */
  public static final String LOCAL_TARGET_NAME = "@@@LOCAL@@@";

  private LocalTargetType() {
  }
}
