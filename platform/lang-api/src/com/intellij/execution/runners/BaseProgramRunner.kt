// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings

abstract class BaseProgramRunner<Settings : RunnerSettings?> : ProgramRunner<Settings> {
  @Throws(ExecutionException::class)
  override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?) {
    val state = environment.state ?: return
    execute(environment, callback, state)
  }

  @Throws(ExecutionException::class)
  protected abstract fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState)
}