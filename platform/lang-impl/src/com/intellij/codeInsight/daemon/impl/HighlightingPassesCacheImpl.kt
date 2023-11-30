// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.HighlightingPassesCache
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.util.applyIf
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable

private val LOG = logger<HighlightingPassesCache>()

private class HighlightingPassesCacheImpl(val project: Project) : HighlightingPassesCache {
  private val experiment = HighlightingPreloadExperiment()

  override fun schedule(files: List<VirtualFile>, sourceOnly: Boolean) {
    if (!Registry.`is`("highlighting.passes.cache")) return

    val registryKeyExperiment = Registry.`is`("highlighting.passes.cache.experiment")
    val linesLimit = Registry.intValue("highlighting.passes.cache.file.size.limit")
    val cacheSize = Registry.intValue("highlighting.passes.cache.size")

    if (!registryKeyExperiment && !experiment.isExperimentEnabled) return

    if (registryKeyExperiment) {
      LOG.debug("Highlighting Passes Cache is enabled in Registry.")
    }

    if (files.isEmpty()) {
      LOG.debug("Highlighting Passes Cache: no files to preload.")
      return
    }

    (ProgressManager.getInstance().progressIndicator as? HighlightingPassIndicator)?.cancel()

    project.messageBus.connect().subscribe(FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        // cancels all pending tasks
        WriteAction.run<Throwable> {  }
      }
    })

    val shortFiles = files.applyIf(sourceOnly) { filterSourceFiles(project, files, linesLimit, cacheSize) }

    val title = CodeInsightBundle.message("title.checking.code.highlightings.in.background.task")
    val task = object : Task.Backgroundable(project, title, true) {
      override fun run(indicator: ProgressIndicator) {
        val currentProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val runner = MainPassesRunner(project, CodeInsightBundle.message("checking.code.highlightings.in.background"), currentProfile)

        runner.runMainPasses(shortFiles)
      }
    }

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, HighlightingPassIndicator())
  }
}

private class HighlightingPassIndicator : ProgressIndicatorBase()

private fun filterSourceFiles(project: Project, files: List<VirtualFile>, linesLimit: Int, cacheSize: Int): List<VirtualFile> {
  return ReadAction.nonBlocking(Callable { files.filter { ProjectFileIndex.getInstance(project).isInSourceContent(it) } })
    .submit(AppExecutorUtil.getAppExecutorService()).get()
    .filter { LoadTextUtil.loadText(it).lines().size < linesLimit }
    .take(cacheSize)
}
