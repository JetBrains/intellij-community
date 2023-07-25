// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.HighlightingPassesCache
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.util.applyIf
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.concurrent.Callable

private val LOG = logger<HighlightingPassesCache>()

private class HighlightingPassesCacheImpl(val project: Project, private val scope: CoroutineScope) : HighlightingPassesCache {
  private val experiment = HighlightingPreloadExperiment()

  override fun schedule(files: List<VirtualFile>, sourceOnly: Boolean) {
    if (!Registry.`is`("highlighting.passes.cache")) {
      return
    }

    val registryKeyExperiment = Registry.`is`("highlighting.passes.cache.experiment")
    val linesLimit = Registry.intValue("highlighting.passes.cache.file.size.limit")
    val cacheSize = Registry.intValue("highlighting.passes.cache.size")

    if (!registryKeyExperiment && !experiment.isExperimentEnabled) {
      return
    }
    if (registryKeyExperiment) {
      LOG.debug("Highlighting Passes Cache is enabled in Registry.")
    }

    if (files.isEmpty()) return

    val filtered = files.applyIf(sourceOnly) { filterSourceFiles(project, files)}

    scope.launch {
      val shortFiles = withContext(Dispatchers.IO) {
        filtered.filter { Files.lines(it.toNioPath()).count() < linesLimit }
      }.take(cacheSize)

      val currentProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
      val runner = MainPassesRunner(project, CodeInsightBundle.message("checking.code.highlightings.in.background"), currentProfile)

      ProgressManager.getInstance().runProcess(
        {
          runner.runMainPasses(shortFiles)
        }, ProgressIndicatorBase())
    }
  }
}

private fun filterSourceFiles(project: Project, files: List<VirtualFile>): List<VirtualFile> {
  return ReadAction.nonBlocking(Callable {
    runBlockingCancellable {
      files.filter {
        readAction { ProjectFileIndex.getInstance(project).isInSourceContent(it) }
      }
    }
  }).submit(AppExecutorUtil.getAppExecutorService()).get()
}
