// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.ApiStatus

/**
 * Represents created target environment. It might be local machine,
 * local or remote Docker container, SSH machine or any other machine
 * that is able to run processes on it.
 */
@ApiStatus.Experimental
abstract class TargetEnvironment(
  open val request: TargetEnvironmentRequest
) {

  @Throws(ExecutionException::class)
  abstract fun createProcess(commandLine: TargetedCommandLine,
                             indicator: ProgressIndicator): Process

  abstract val remotePlatform: TargetPlatform

  //FIXME: document
  abstract fun shutdown()

}