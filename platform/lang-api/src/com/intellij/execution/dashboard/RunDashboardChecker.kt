// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface RunDashboardChecker {
  val typeId: String

  fun countUniqueConfigurations(project: Project): Int
}