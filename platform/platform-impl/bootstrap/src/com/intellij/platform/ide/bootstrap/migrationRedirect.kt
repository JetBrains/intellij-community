// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.idea.AppMode
import com.intellij.openapi.application.MigrationRedirect
import com.intellij.openapi.application.MigrationRedirectChoice
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.isMigrationRedirectStart
import com.intellij.openapi.application.launchMigrationRedirect
import com.intellij.openapi.application.loadMigrationRedirect
import com.intellij.openapi.application.migrationStateFile
import com.intellij.openapi.application.showMigrationRedirect
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.util.system.OS
import com.intellij.util.ui.RawSwingDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import kotlin.system.exitProcess

// a getter, because the startup loads this file before the log is set up
private val LOG: Logger
  get() = logger<MigrationRedirect>()

/**
 * The startup part of the migration.
 * [redirect] gives the redirect of this start, or `null`. [job] shows the window and starts the Unified IDE.
 */
internal class MigrationRedirectJobs(val redirect: Deferred<MigrationRedirect?>, val job: Job)

/**
 * After the migration, a start of the old IDE on macOS shows the redirect window before the application exists.
 * A close of the window exits the process with the exit code 0.
 * The primary button starts the Unified IDE and exits the process. When that start fails, the usual start continues.
 */
internal fun scheduleMigrationRedirect(
  scope: CoroutineScope,
  args: List<String>,
  isHeadless: Boolean,
  configImportNeededDeferred: Deferred<Boolean>,
  lockSystemDirsJob: Job,
  logDeferred: Deferred<Logger>,
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
    val redirect = span("migration redirect loading", Dispatchers.IO) {
      loadMigrationRedirect(isMigrationRedirectStart(args), stateFile)
    }
    if (redirect != null) {
      hideSplash()
    }
    redirect
  }
  val job = scope.launch(CoroutineName("migration redirect")) {
    val redirect = redirectDeferred.await() ?: return@launch
    initLafJob.join()
    val choice = withContext(RawSwingDispatcher) {
      showMigrationRedirect(redirect.content)
    }
    if (choice == MigrationRedirectChoice.EXIT) {
      exitProcess(0)
    }
    val sourceProduct = appInfoDeferred.await().build.productCode
    val isLaunched = try {
      launchMigrationRedirect(redirect, sourceProduct, args)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("the launch of the Unified IDE failed, the usual start continues", e)
      false
    }
    if (isLaunched) {
      exitProcess(0)
    }
  }
  return MigrationRedirectJobs(redirectDeferred, job)
}
