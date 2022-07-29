// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.concurrency.await
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import java.util.concurrent.TimeUnit

internal fun loadDependenciesSync(project: Project) {
  val libs = RepositoryLibrarySynchronizer.collectLibrariesToSync(project)
  if (libs.isEmpty()) return

  val timeout = Registry.intValue("load.maven.dependencies.timeout", 120).toLong()
  runBlocking {
    try {
      withTimeout(TimeUnit.MINUTES.toMillis(timeout)) {
        submitLoadJobs(project, libs)
      }
    }
    catch (e: TimeoutCancellationException) {
      thisLogger().error("Cant resolve maven dependencies within $timeout minutes")
    }
  }
}

private suspend fun submitLoadJobs(project: Project, libs: Collection<Library>) {
  for (library in libs) {
    if (LibraryTableImplUtil.isValidLibrary(library)) {
      runCatching {
        RepositoryUtils.reloadDependencies(project, library as LibraryEx).await()
      }
        .fold(onSuccess = { it }, 
              onFailure = {
                Logger.getInstance("syncLibrariesLoading").error(it)
                null
             })
    }
  }
}
