// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * A [RunConfiguration] that executes the content of a user file named in its settings.
 */
@ApiStatus.Internal
interface RunConfigurationWithTargetFile {
  /**
   * Returns the file whose content this configuration executes.
   */
  fun getTargetFile(): VirtualFile?
}
