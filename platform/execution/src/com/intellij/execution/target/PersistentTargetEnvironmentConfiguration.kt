// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

/**
 * Run Target configurations implementing this interface aim to long-running machines. Processes and files created inside
 * a single [TargetEnvironment] outlive the environment and can be accessed in further target environments.
 */
interface PersistentTargetEnvironmentConfiguration {
  /**
   * Returns `true` if processes and files in this particular configuration outlive the instance of [TargetEnvironment]
   * where they were created.
   */
  val isPersistent: Boolean
}

/** See [PersistentTargetEnvironmentConfiguration]. */
val TargetEnvironmentConfiguration?.isPersistent: Boolean
  get() = this == null || this is PersistentTargetEnvironmentConfiguration && isPersistent