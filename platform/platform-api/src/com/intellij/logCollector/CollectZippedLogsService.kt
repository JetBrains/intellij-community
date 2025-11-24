// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logCollector

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Service for collecting and packaging IDE logs and diagnostic data.
 */
@ApiStatus.Internal
interface CollectZippedLogsService {
  /**
   * Collects logs and diagnostic data, packages them into a zip file, and reveals the file to the user.
   *
   * @param project The project context, or null for application-level logs
   */
  fun collectZippedLogs(project: Project?)
}