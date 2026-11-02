// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.flow.debounceBatch
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtilCore
import com.intellij.util.containers.forEachLoggingErrors
import com.intellij.util.gist.GistManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.EnumSet
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class StubRecoveryServiceImpl(coroutineScope: CoroutineScope) : StubRecoveryService {
  companion object {
    private val LOG = logger<StubRecoveryServiceImpl>()
  }

  private class Request(
    val file: VirtualFile,
    val project: WeakReference<Project>?,
    val extraSteps: Set<StubRecoveryExtraStep>,
  )

  private class Batch(requests: List<Request>) {
    val projectsToDropPsiCache = WeakHashMap<Project, Unit>()
    val saveDocument = mutableSetOf<VirtualFile>()
    val reparse = mutableSetOf<VirtualFile>()

    init {
      for (request in requests) {
        for (extraStep in request.extraSteps) {
          when (extraStep) {
            StubRecoveryExtraStep.DROP_PSI_CACHES -> {
              val project = request.project?.get()
              if (project != null) projectsToDropPsiCache[project] = Unit
            }
            StubRecoveryExtraStep.SAVE_DOCUMENT -> saveDocument.add(request.file)
            StubRecoveryExtraStep.REPARSE_FILE -> reparse.add(request.file)
          }
        }
      }
    }
  }

  private val recoveryRequests = Channel<Request>(Channel.UNLIMITED)

  private val worker = coroutineScope.launch {
    recoveryRequests.receiveAsFlow().debounceBatch(100.milliseconds).collect { requests ->
      val batch = Batch(requests)
      try {
        withContext(ModalityState.nonModal().asContextElement()) {
          repairBatch(batch)
        }
      }
      catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
      }
    }
  }.apply {
    invokeOnCompletion { recoveryRequests.cancel() }
  }

  override fun scheduleReindexing(project: Project?, file: VirtualFile, vararg extraSteps: StubRecoveryExtraStep) {
    if (!StubRecoveryService.isRecoveryEnabled() || !file.isValid || !worker.isActive) return

    LOG.runAndLogException {
      LOG.runAndLogException { serviceOrNull<GistManager>()?.mergeDependentCacheInvalidations() }.use { // avoid WA on EDT
        ProgressManager.getInstance().executeNonCancelableSection {
          StubTreeLoader.getInstance().rebuildStubTree(file)
        }
      }
    }
    if (extraSteps.isEmpty()) return

    val extraStepSet = EnumSet.noneOf(StubRecoveryExtraStep::class.java)
    extraStepSet.addAll(extraSteps)
    val projectRef = if (project?.isDisposed == false) WeakReference(project) else null

    val request = Request(file, projectRef, extraStepSet)
    recoveryRequests.trySend(request)
  }

  private suspend fun repairBatch(batch: Batch) {
    val context = currentCoroutineContext()
    writeIntentReadAction {
      context.ensureActive()
      LOG.runAndLogException { serviceOrNull<GistManager>()?.mergeDependentCacheInvalidations() }.use {
        batch.projectsToDropPsiCache.keys.forEachLoggingErrors(LOG) { project ->
          context.ensureActive()
          if (!project.isDisposed) PsiManager.getInstance(project).dropPsiCaches()
        }
        batch.saveDocument.forEachLoggingErrors(LOG) { file ->
          context.ensureActive()
          val manager = FileDocumentManager.getInstance()
          manager.getCachedDocument(file)?.let { manager.saveDocument(it) }
        }
        context.ensureActive()
        if (batch.reparse.isNotEmpty()) {
          LOG.runAndLogException { FileContentUtilCore.reparseFiles(batch.reparse) }
        }
      }
    }
  }
}
