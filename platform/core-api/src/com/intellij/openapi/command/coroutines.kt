// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command

import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Runs given [action] as a [write command][com.intellij.openapi.command.WriteCommandAction].
 *
 * @see com.intellij.openapi.application.writeAction
 */
@ApiStatus.Experimental
suspend fun <T> writeCommandAction(project: Project, @NlsContexts.Command commandName: String, action: () -> T): T {
  return withContext(Dispatchers.EDT) {
    blockingContext {
      WriteCommandAction.writeCommandAction(project)
        .withName(commandName)
        .compute<T, Throwable>(action)
    }
  }
}

/**
 * Runs given [action] with [write command][com.intellij.openapi.command.WriteCommandAction] described via the receiver builder.
 *
 * @see com.intellij.openapi.application.writeAction
 */
@ApiStatus.Experimental
suspend fun <T> WriteCommandAction.Builder.execute(action: () -> T): T {
  val builder = this

  return withContext(Dispatchers.EDT) {
    blockingContext {
      builder.compute<T, Throwable>(action::invoke)
    }
  }
}