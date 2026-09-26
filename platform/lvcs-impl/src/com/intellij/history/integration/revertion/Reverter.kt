// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.revertion

import com.intellij.history.core.revisions.Revision
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThrowableRunnable
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.IOException

abstract class Reverter internal constructor(
  private val project: Project,
  protected val gateway: IdeaGateway,
  private val commandNameSupplier: () -> @NlsContexts.Command String,
) {
  @Throws(IOException::class)
  fun checkCanRevert(): List<String> {
    if (!askForReadOnlyStatusClearing()) {
      return listOf(LocalHistoryBundle.message("revert.error.files.are.read.only"))
    }
    return listOf()
  }

  protected fun askForReadOnlyStatusClearing(): Boolean {
    return gateway.ensureFilesAreWritable(project, filesToClearROStatus)
  }

  protected abstract val filesToClearROStatus: List<VirtualFile>

  /**
   * Prefer suspending variant where possible
   */
  @ApiStatus.Obsolete
  @Throws(Exception::class)
  fun revert() {
    try {
      WriteCommandAction.writeCommandAction(project).withName(commandName).run(ThrowableRunnable {
        gateway.saveAllUnsavedDocuments()
        doRevert()
        gateway.saveAllUnsavedDocuments()
      })
    }
    catch (e: RuntimeException) {
      val cause = e.cause
      if (cause is IOException) {
        throw cause
      }
      throw e
    }
  }

  @Throws(Exception::class)
  suspend fun performRevert() {
    try {
      writeCommandAction(project, commandName) {
        gateway.saveAllUnsavedDocuments()
        doRevert()
        gateway.saveAllUnsavedDocuments()
      }
    }
    catch (e: RuntimeException) {
      val cause = e.cause
      if (cause is IOException) {
        throw cause
      }
      throw e
    }
  }

  @get:NlsContexts.Command
  val commandName: String get() = commandNameSupplier()

  @Throws(IOException::class)
  protected abstract fun doRevert()

  companion object {
    @JvmStatic
    @Nls
    fun getRevertCommandName(to: Revision): @Nls String {
      val name = to.changeSetName
      val date = DateFormatUtil.formatDateTime(to.getTimestamp())
      if (name != null) {
        return LocalHistoryBundle.message("activity.name.revert.to.change.date", name, date)
      }
      return LocalHistoryBundle.message("activity.name.revert.to.date", date)
    }
  }
}
