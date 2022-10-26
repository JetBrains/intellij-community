// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.execution.target.value.tryMapToSynchronizedVolume
import java.nio.file.Path

/**
 * Configuration that is accessible from local machine via FS, like ``\\wsl$``
 */
interface TargetConfigurationWithLocalFsAccess : ExternallySynchronized {
  val asTargetConfig: TargetEnvironmentConfiguration
  val platform: TargetPlatform
}

fun TargetConfigurationWithLocalFsAccess.tryMapToSynchronizedVolume(localPath: Path): FullPathOnTarget? =
  synchronizedVolumes.tryMapToSynchronizedVolume(localPath, platform)