// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/** Key in [Project]: `null` before first scanning => [FirstScanningState.REQUESTED] => [FirstScanningState.PERFORMED] */
@JvmField
internal val FIRST_SCANNING_REQUESTED: Key<FirstScanningState> = Key.create("FIRST_SCANNING_REQUESTED")

internal enum class FirstScanningState {
  REQUESTED, PERFORMED
}

internal fun isFirstProjectScanningRequested(project: Project): Boolean {
  return project.getUserData(FIRST_SCANNING_REQUESTED) != null
}

@ApiStatus.Internal
fun isFirstProjectScanningPerformed(project: Project): Boolean {
  return project.getUserData(FIRST_SCANNING_REQUESTED) == FirstScanningState.PERFORMED
}
