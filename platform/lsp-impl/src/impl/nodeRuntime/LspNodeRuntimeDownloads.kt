// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.nodeRuntime

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val logger = logger<LspNodeRuntimeDownloads>()

/**
 * Starts an LSP client that needs Node.js, and downloads the runtime first if it is absent.
 *
 * [com.intellij.platform.lsp.api.LspIntegrationProvider.fileOpened] cannot wait: it must not block the
 * UI, and its [LspIntegrationProvider.LspClientStarter] stops working the moment it returns. So
 * [startClient] is a thunk that runs on one of two paths:
 * - The runtime is on disk. [startClient] runs now, inside the current `fileOpened` call, where the
 *   client starter still works.
 * - The runtime is absent. This function drops [startClient] and joins the one background download.
 *   After the download it calls [LspClientManager.startClientsIfNeeded] for this provider, which calls
 *   `fileOpened` again for every open file. The second call takes the first path, and builds the
 *   descriptor again. Every provider shares that download, and each one gets its own restart.
 *
 * The `fileOpened` documentation describes this two-call shape. This function is that shape, with the
 * state it needs kept in one place instead of in each provider.
 *
 * ```kotlin
 * override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
 *   if (isFooLspFile(file)) {
 *     withNodeRuntimeEnsured(project) {
 *       clientStarter.ensureClientStarted(FooLspServerDescriptor(project))
 *     }
 *   }
 * }
 * ```
 *
 * A descriptor started this way reads the runtime with [LspNodeRuntimeManager.getRuntime], which
 * cannot be null on the path that runs [startClient].
 *
 * @param project the project the download progress belongs to, and the project whose clients restart
 * @param startClient starts the client. It runs at most once per `fileOpened` call, and only with the
 * runtime present.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun LspIntegrationProvider.withNodeRuntimeEnsured(project: Project, startClient: () -> Unit) {
  if (LspNodeRuntimeManager.getInstance().getRuntime() != null) {
    startClient()
    return
  }
  if (project.isDefault || project.isDisposed) return
  LspNodeRuntimeDownloads.getInstance(project).restartWhenRuntimeReady(javaClass)
}

/**
 * Downloads the Node.js runtime once, and restarts the clients of every provider that waits for it.
 *
 * There is one pinned runtime, so there is one download. Only the restart is per provider, because
 * [LspClientManager.startClientsIfNeeded] takes one provider class.
 */
@Service(Service.Level.PROJECT)
internal class LspNodeRuntimeDownloads(private val project: Project) {

  companion object {
    fun getInstance(project: Project): LspNodeRuntimeDownloads = project.service()
  }

  /**
   * The providers whose clients start as soon as the runtime is ready.
   *
   * `fileOpened` runs for every open file and for every provider, so a project with ten open files
   * adds the same provider ten times. A set of provider classes therefore holds at most one entry per
   * provider, and each one gets one restart.
   */
  private val providersToRestart = ConcurrentHashMap.newKeySet<Class<out LspIntegrationProvider>>()

  /** Whether a download task runs now. It guards the task, never the set. */
  private val downloadRunning = AtomicBoolean(false)

  /**
   * Registers [providerClass] for a restart, and starts the download when no task runs yet.
   *
   * A provider that arrives while the download runs joins it. It needs no task and no second progress
   * bar, and the restart below still reaches it.
   *
   * A failed download leaves nothing behind, so the next opened file asks again. There is no retry
   * loop and no back-off, which matches the ACP manager this code comes from.
   */
  fun restartWhenRuntimeReady(providerClass: Class<out LspIntegrationProvider>) {
    providersToRestart.add(providerClass)
    if (!downloadRunning.compareAndSet(false, true)) {
      logger.debug("A Node.js download already runs. ${providerClass.name} waits for it.")
      return
    }

    ProgressManager.getInstance().run(object : Task.Backgroundable(
      project,
      LspBundle.message("lsp.runtime.node.progress.title"),
      true,
    ) {
      override fun run(indicator: ProgressIndicator) {
        val runtime = LspNodeRuntimeManager.getInstance().ensureRuntime(indicator)
        if (runtime == null) {
          logger.warn("No Node.js runtime. The LSP clients of $providersToRestart stay down.")
          return
        }
        logger.info("Node.js v${runtime.version} is ready. Starting the clients of $providersToRestart.")
        val clientManager = LspClientManager.getInstance(project)
        // Takes each provider out of the set, so a provider that joins during this loop gets a
        // restart too.
        while (true) {
          val waiting = providersToRestart.firstOrNull() ?: break
          providersToRestart.remove(waiting)
          clientManager.startClientsIfNeeded(waiting)
        }
      }

      override fun onFinished() {
        // A provider still in the set waited for a download that failed, or that the user stopped. It
        // gets another chance from the next opened file, which finds an empty set and a free flag.
        providersToRestart.clear()
        downloadRunning.set(false)
      }
    })
  }
}
