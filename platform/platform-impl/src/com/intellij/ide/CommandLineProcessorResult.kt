// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Future

@ApiStatus.Internal
data class CommandLineProcessorResult(val project: Project?, val future: Future<CliResult>) {
  companion object {
    @JvmStatic
    fun createError(message: String): CommandLineProcessorResult {
      return CommandLineProcessorResult(null, CliResult.error(1, message))
    }
  }

  val hasError: Boolean
    get() = future.isDone && future.get().exitCode == 1

  fun showErrorIfFailed(): Boolean {
    if (future.isDone) {
      val result = future.get()
      if (result.exitCode == 1) {
        Messages.showErrorDialog(result.message, "Cannot execute command")
        return true
      }
    }

    return false
  }
}