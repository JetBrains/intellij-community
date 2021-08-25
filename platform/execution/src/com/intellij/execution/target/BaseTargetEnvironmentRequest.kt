// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

abstract class BaseTargetEnvironmentRequest @JvmOverloads constructor(
  override val uploadVolumes: MutableSet<TargetEnvironment.UploadRoot> = HashSet(),
  override val downloadVolumes: MutableSet<TargetEnvironment.DownloadRoot> = HashSet(),
  override val targetPortBindings: MutableSet<TargetEnvironment.TargetPortBinding> = HashSet(),
  override val localPortBindings: MutableSet<TargetEnvironment.LocalPortBinding> = HashSet(),
  override var projectPathOnTarget: String = ""
) : TargetEnvironmentRequest {
  private val environmentPreparedCallbacks = mutableListOf<(TargetEnvironment, TargetProgressIndicator) -> Unit>()

  override fun onEnvironmentPrepared(callback: (environment: TargetEnvironment, progressIndicator: TargetProgressIndicator) -> Unit) {
    environmentPreparedCallbacks.add(callback)
  }

  fun environmentPrepared(environment: TargetEnvironment,
                          progressIndicator: TargetProgressIndicator) {
    for (callback in environmentPreparedCallbacks) {
      callback(environment, progressIndicator)
    }
  }
}