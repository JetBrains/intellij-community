// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.performancePlugin

import com.intellij.jarRepository.loadDependenciesSync
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import org.jetbrains.concurrency.resolvedPromise

internal class SyncJpsLibrariesCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX: String = "${CMD_PREFIX}syncJpsLibraries"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    try {
      loadDependenciesSync(context.project)
      return resolvedPromise()
    }
    catch (e: Throwable) {
      return rejectedPromise(e)
    }
  }
}