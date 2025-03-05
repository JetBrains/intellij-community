// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import org.jetbrains.annotations.ApiStatus

/**
 * Might be implemented by specific [TargetEnvironment] to describe the environment with externally synchronized volumes.
 */
@ApiStatus.Experimental
interface ExternallySynchronized {
  val synchronizedVolumes: List<TargetEnvironment.SynchronizedVolume>
}