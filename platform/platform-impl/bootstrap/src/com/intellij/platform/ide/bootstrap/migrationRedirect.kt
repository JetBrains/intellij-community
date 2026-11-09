// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)
package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.CliResult
import com.intellij.idea.AppMode
import com.intellij.idea.IdeStarter
import com.intellij.openapi.application.MigrationOpenRequests
import com.intellij.openapi.application.MigrationRedirect
import com.intellij.openapi.application.MigrationRedirectChoice
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.launchMigrationRedirect
import com.intellij.openapi.application.loadMigrationRedirect
import com.intellij.openapi.application.migrationRedirectArguments
import com.intellij.openapi.application.migrationRedirectStart
import com.intellij.openapi.application.migrationStateFile
import com.intellij.openapi.application.showMigrationRedirect
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.protocolCommand
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.intellij.util.ui.RawSwingDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// a getter, because the startup loads this file before the log is set up
private val LOG: Logger
  get() = logger<MigrationRedirect>()

// the time to wait for an open event that comes after the events that macOS gave before the handlers were set
private val OPEN_EVENTS_GRACE: Duration = 100.milliseconds
private const val NS_APPLICATION_ACTIVATION_POLICY_ACCESSORY = 1L

/**
 * The startup part of the migration.
 * [redirect] gives what the migration does with this start, or `null`. [job] shows the window and starts the Unified IDE.
 */
internal class MigrationRedirectJobs(val redirect: Deferred<MigrationRedirect?>, val job: Job)

/**
 * After the migration, a start of the old IDE on macOS shows the redirect window, or starts the Unified IDE directly.
 * Both come before the application exists. A close of the window exits the process with the exit code 0.
 * A successful start of the Unified IDE exits the process. When that start fails, the usual start continues.
 * Until then, a later start of the old IDE goes to the Unified IDE too.
 */
internal fun scheduleMigrationRedirect(
  scope: CoroutineScope,
  args: List<String>,
  isHeadless: Boolean,
  configImportNeededDeferred: Deferred<Boolean>,
  lockSystemDirsJob: Job,
  logDeferred: Deferred<Logger>,
  initAwtToolkitJob: Job,
  initLafJob: Job,
  appInfoDeferred: Deferred<ApplicationInfoEx>,
): MigrationRedirectJobs {
  val redirectDeferred = scope.async(CoroutineName("migration redirect decision")) {
    if (OS.CURRENT != OS.macOS || isHeadless || AppMode.isRemoteDevHost() || configImportNeededDeferred.await()) {
      return@async null
    }
    // a second start gives its arguments to the running IDE and exits while the directories are locked
    lockSystemDirsJob.join()
    val stateFile = migrationStateFile()
    // nearly all starts have no state file; they write no log line, so they do not wait for the logger
    if (span("migration redirect checking", Dispatchers.IO) { Files.notExists(stateFile) }) {
      return@async null
    }
    logDeferred.join()
    val redirect = try {
      span("migration redirect loading", Dispatchers.IO) {
        loadMigrationRedirect(migrationRedirectStart(args), stateFile)
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("the kind of this start for the migration redirect is unknown, the usual start continues", e)
      null
    }
    if (redirect != null) {
      hideSplash()
      setActivationListener { request -> scope.async { forwardLaterStart(request, appInfoDeferred.await().build.productCode) } }
    }
    redirect
  }

  val job = scope.launch(CoroutineName("migration redirect")) {
    val redirect = redirectDeferred.await() ?: return@launch
    val content = redirect.content
    if (content != null) {
      initLafJob.join()
      val choice = withContext(RawSwingDispatcher) {
        showMigrationRedirect(content)
      }
      if (choice == MigrationRedirectChoice.EXIT) {
        exitProcess(0)
      }
    }

    val sourceProduct = appInfoDeferred.await().build.productCode
    val arguments = migrationRedirectArguments(args)
    // Finder and the Dock give the files and the URLs of a start with no arguments as Apple events
    val openRequests = if (arguments.isEmpty()) {
      span("migration redirect events") {
        collectOpenRequests(initAwtToolkitJob)
      }
    }
    else null
    val exitCode = try {
      span("migration redirect launch") {
        launchMigrationRedirect(redirect, sourceProduct, arguments + (openRequests?.toArguments() ?: emptyList()),
                                onStart = { hideDockIcon(initAwtToolkitJob) })
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("the launch of the Unified IDE failed, the usual start continues", e)
      null
    }
    if (exitCode == null) {
      setActivationListener(notInitializedProcessor)
      openRequests?.let(::giveToUsualStart)
      return@launch
    }
    exitProcess(exitCode)
  }
  return MigrationRedirectJobs(redirectDeferred, job)
}

/**
 * Gives a later start of this IDE to the Unified IDE, while the redirect window shows or a start waits for the Unified IDE.
 * [request] is the working directory of the later start and its arguments.
 * A start that the migration does not redirect gets the usual result.
 */
private suspend fun forwardLaterStart(request: List<String>, sourceProduct: String): CliResult {
  try {
    val currentDir = Path.of(request.first())
    val args = request.drop(1)
    val redirect = withContext(Dispatchers.IO) { loadMigrationRedirect(migrationRedirectStart(args, currentDir)) }
    if (redirect != null) {
      LOG.info("a later start goes to the Unified IDE: ${args}")
      val exitCode = launchMigrationRedirect(redirect, sourceProduct, migrationRedirectArguments(args, currentDir))
      if (exitCode != null) {
        return CliResult(exitCode, null)
      }
    }
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("a later start cannot go to the Unified IDE", e)
  }
  return notInitializedProcessor(request).await()
}

/**
 * Takes the open-file and the open-URI events of this start. JBR keeps such an event until the first handler is set.
 * So the startup must call it before the handlers of `initMacApplication`.
 * A failure is logged and gives no requests.
 */
private suspend fun collectOpenRequests(initAwtToolkitJob: Job): MigrationOpenRequests {
  val files = ArrayList<Path>()
  var uri: String? = null
  try {
    // AppKit gives the events only after the toolkit exists
    initAwtToolkitJob.join()
    val start = System.nanoTime()
    withContext(RawSwingDispatcher) {
      if (!Desktop.isDesktopSupported()) {
        return@withContext
      }
      val desktop = Desktop.getDesktop()
      // the handlers and the code below run on the EDT, so the lists need no lock
      if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
        desktop.setOpenFileHandler { event ->
          LOG.info("the open-file event for the migration redirect: ${event.files}, ${elapsedMs(start)} ms after the handler")
          event.files.mapTo(files) { it.toPath() }
        }
      }
      if (desktop.isSupported(Desktop.Action.APP_OPEN_URI)) {
        desktop.setOpenURIHandler { event ->
          LOG.info("the open-URI event for the migration redirect: ${event.uri}, ${elapsedMs(start)} ms after the handler")
          uri = event.uri.toString()
        }
      }
    }
    // JBR gives the kept events with `invokeLater` when a handler is set, so this empty block runs after them
    withContext(RawSwingDispatcher) {}
    delay(OPEN_EVENTS_GRACE)
    return withContext(RawSwingDispatcher) {
      LOG.info("the migration redirect takes ${files.size} files and ${if (uri == null) "no" else "a"} URL, ${elapsedMs(start)} ms after the handler")
      MigrationOpenRequests(files.toList(), uri)
    }
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("cannot take the open events for the migration redirect", e)
    return withContext(RawSwingDispatcher) { MigrationOpenRequests(files.toList(), uri) }
  }
}

private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000

/** The launch failed, so the usual start opens the files and the URL, as the handlers of `initMacApplication` do. */
private fun giveToUsualStart(requests: MigrationOpenRequests) {
  if (requests.files.isNotEmpty()) {
    IdeStarter.openFilesOnLoading(requests.files)
  }
  requests.uri?.let { IdeStarter.openUriOnLoading(protocolCommand(URI(it))) }
}

/**
 * Removes the icon of this IDE from the Dock while this process waits for the Unified IDE.
 * The call needs the AppKit main loop, so it waits for the toolkit, and it does not wait for the main thread.
 */
private suspend fun hideDockIcon(initAwtToolkitJob: Job) {
  initAwtToolkitJob.join()
  if (initAwtToolkitJob.isCancelled) {
    LOG.info("the toolkit is not initialized, the Dock icon stays")
    return
  }
  try {
    Foundation.executeOnMainThread(false, false) {
      try {
        val app = Foundation.invoke("NSApplication", "sharedApplication")
        Foundation.invoke(app, "setActivationPolicy:", NS_APPLICATION_ACTIVATION_POLICY_ACCESSORY)
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.warn("cannot remove the Dock icon", e)
      }
    }
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("cannot remove the Dock icon", e)
  }
}
