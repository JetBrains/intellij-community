// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import java.nio.file.Path

/**
 * Configuration that is accessible from local machine via FS, like ``\\wsl$`.
 * If you implement it, make sure you implement ``equals``, so we one could find all instances for the certain target
 */
interface TargetConfigurationWithLocalFsAccess {
  /**
   * Kotlin doesn't support type intersection, so return "this" here
   */
  val asTargetConfig: TargetEnvironmentConfiguration

  /**
   * If [probablyPathOnTarget] points to this target (like ``\\wsl$\foo\bar``) return target path (``/foo/bar``).
   * If path is not on target  (like ``c:\`` in Windows) returns null.
   */
  fun getTargetPathIfLocalPathIsOnTarget(probablyPathOnTarget: Path): FullPathOnTarget?
}