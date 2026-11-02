// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.WrappedProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/** Extra steps that run before mandatory reindexing. Each step is independent. */
@ApiStatus.Internal
enum class StubRecoveryExtraStep {
  DROP_PSI_CACHES,
  SAVE_DOCUMENT,
  REPARSE_FILE,
}

/** Coordinates deferred repairs without retaining PSI or retrying the failed operation. */
@ApiStatus.Internal
interface StubRecoveryService {
  /** Marks the progress of daemon highlighting. */
  interface HighlightingProgress

  /**
   * Schedules [file] for reindexing synchronously and runs the extra recovery steps asynchronously.
   */
  fun scheduleReindexing(project: Project?, file: VirtualFile, vararg extraSteps: StubRecoveryExtraStep)

  companion object {
    /** Logs an inconsistency at debug level during highlighting when [project] can run smart and error reporting is disabled. */
    @JvmStatic
    @JvmOverloads
    fun logStubInconsistency(project: Project, logger: Logger, message: String, cause: Throwable? = null) {
      var indicator = ProgressManager.getGlobalProgressIndicator()
      while (indicator is WrappedProgressIndicator) indicator = indicator.originalProgressIndicator
      val dumbService = DumbService.getInstance(project)
      val canRunSmart = dumbService.canRunSmart()

      val message = "$message, isDumb= ${dumbService.isDumb}, canRunSmart= $canRunSmart"

      if (indicator is HighlightingProgress &&
          Registry.`is`("daemon.suppress.stub.inconsistency.errors", true) &&
          canRunSmart) {
        logger.debug(message, cause)
      }
      else {
        logger.error(message, cause)
      }
    }

    /** Schedules [file] for recovery if the service is available. */
    @JvmStatic
    fun scheduleReindexing(file: PsiFile, vararg extraSteps: StubRecoveryExtraStep) {
      val virtualFile = file.virtualFile ?: return
      getInstanceOrNull()?.scheduleReindexing(file.project, virtualFile, *extraSteps)
    }

    /** Returns whether automatic recovery is enabled. */
    @JvmStatic
    fun isRecoveryEnabled(): Boolean = !java.lang.Boolean.getBoolean("skip.index.repair")

    @JvmStatic
    fun getInstance(): StubRecoveryService = ApplicationManager.getApplication().service<StubRecoveryService>()

    /** Returns the service, or `null` if the application does not provide stub recovery. */
    @JvmStatic
    fun getInstanceOrNull(): StubRecoveryService? = ApplicationManager.getApplication().serviceOrNull<StubRecoveryService>()
  }
}
