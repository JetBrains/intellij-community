// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import org.jetbrains.annotations.NonNls

typealias TargetAndTypeId = Pair<TargetId, @NonNls String>

/**
 * Target configuration that could be identified among other configs by this type using [targetId]
 */
abstract class TargetConfigurationWithId(typeId: String) : TargetEnvironmentConfiguration(typeId) {

  /**
   * Unique ID of this config i.e WSL distro name, ssh full url etc
   */
  protected abstract val targetId: TargetId

  /**
   * Unlike [TargetEnvironmentConfiguration.uuid], this field is not regenerated each time and points to the same config
   * each time
   */
  val targetAndTypeId: TargetAndTypeId get() = Pair(targetId, typeId)
}