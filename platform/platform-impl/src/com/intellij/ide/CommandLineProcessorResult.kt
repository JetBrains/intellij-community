// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
data class CommandLineProcessorResult(val project: Project?, val future: CompletableFuture<CliResult>) {
  constructor(project: Project?, result: CliResult) : this(project, CompletableFuture.completedFuture(result))

  companion object {
    fun createError(@NlsContexts.DialogMessage message : String): CommandLineProcessorResult {
      return CommandLineProcessorResult(project = null, future = CliResult.error(1, message))
    }
  }

  val hasError: Boolean
    get() = future.isDone && future.join().exitCode == 1

  fun showErrorIfFailed(): Boolean {
    if (hasError) {
      Messages.showErrorDialog(future.join().message, IdeBundle.message("dialog.title.cannot.execute.command"))
      return true
    }
    return false
  }
}