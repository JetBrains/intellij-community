// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.readableFs

/**
 * This target configuration provides access to filesystem, so we can check if certain path is file, directory etc
 */
@FunctionalInterface
fun interface TargetConfigurationReadableFs {

  /**
   * Checks [targetPath] against target file system. `null` means file not found.
   */
  fun getPathInfo(targetPath: String): PathInfo?

}