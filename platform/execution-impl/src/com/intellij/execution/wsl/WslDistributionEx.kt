// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.util.PathMappingSettings
import kotlin.io.path.pathString

/**
 * Win drives -> /mnt
 */
val WSLDistribution.rootMappings: List<PathMappingSettings.PathMapping>
  get() = listWindowsRoots().map { PathMappingSettings.PathMapping(it.pathString, getWslPath(it.pathString)) } + listOf(
    PathMappingSettings.PathMapping(getWindowsPath("/"), "/")
  )