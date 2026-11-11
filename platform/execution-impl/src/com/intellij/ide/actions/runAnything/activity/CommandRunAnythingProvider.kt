// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.activity

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.ide.actions.runAnything.RunAnythingAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
abstract class CommandRunAnythingProvider(
  @NonNls private val commandName: String,
) : RunAnythingProviderBase<String>() {
  override fun findMatchingValue(dataContext: DataContext, pattern: String): String? {
    return pattern.takeIf { hasCommandNamePrefix(it, commandName) }
  }

  override fun execute(dataContext: DataContext, value: String) {
    val workingDirectory = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val executor = dataContext.getData(RunAnythingAction.EXECUTOR_KEY) ?: DefaultRunExecutor.getRunExecutorInstance()
    RunAnythingCommandProvider.runCommand(workingDirectory, value, executor, dataContext)
  }

  override fun getCommand(value: String): String = value

  companion object {
    @JvmStatic
    fun hasCommandNamePrefix(@NonNls pattern: String, @NonNls commandName: String): Boolean {
      return pattern.startsWith(commandName) &&
             (pattern.length == commandName.length || pattern[commandName.length] == ' ')
    }
  }
}
