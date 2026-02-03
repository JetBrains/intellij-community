// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.startup

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * An extension point that allows to execute some code or wait for something asynchronously before executing [com.intellij.execution.RunnerAndConfigurationSettings]
 */
interface BeforeRunStartupTasks {

  /**
   * This method will be executed before [com.intellij.execution.RunnerAndConfigurationSettings].
   * */
  @Internal
  suspend fun beforeRun()
}