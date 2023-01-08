// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.annotations.ApiStatus

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
data class CommandLineProcessorResult(val project: Project?, val future: Deferred<CliResult>) {
  constructor(project: Project?, result: CliResult) : this(project, CompletableDeferred(value = result))

  companion object {
    fun createError(@NlsContexts.DialogMessage message : String): CommandLineProcessorResult {
      return CommandLineProcessorResult(project = null, future = CompletableDeferred(CliResult(exitCode = 1, message = message)))
    }
  }

  val hasError: Boolean
    get() = future.isCompleted && future.getCompleted().exitCode == 1

  fun showErrorIfFailed(): Boolean {
    if (hasError) {
      Messages.showErrorDialog(future.getCompleted().message, IdeBundle.message("dialog.title.cannot.execute.command"))
      return true
    }
    return false
  }
}