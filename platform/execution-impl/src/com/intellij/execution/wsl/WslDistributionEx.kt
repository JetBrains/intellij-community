// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.target.TargetEnvironment
import com.intellij.util.PathMappingSettings
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Win drives -> /mnt
 */
val WSLDistribution.rootMappings: List<PathMappingSettings.PathMapping>
  get() = listWindowsLocalDriveRoots().map { PathMappingSettings.PathMapping(it.pathString, getWslPath(it.pathString)) } + listOf(
    PathMappingSettings.PathMapping(getWindowsPath("/"), "/")
  )


/**
 * Same as [rootMappings] but with [TargetEnvironment.SynchronizedVolume]
 */
val WSLDistribution.synchronizedVolumes
  get() = rootMappings.map {
    TargetEnvironment.SynchronizedVolume(Path.of(it.localRoot), it.remoteRoot)
  }