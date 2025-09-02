// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.applications

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.applications.DiffApplicationBase.findFilesOrThrow
import com.intellij.diff.applications.DiffApplicationBase.guessProject
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.BlankDiffWindowUtil.createBlankDiffRequestChain
import com.intellij.diff.util.BlankDiffWindowUtil.setupBlankContext
import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.ide.CliResult
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ApplicationStarterBase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.bootstrap.hideSplashBeforeShow
import com.intellij.ui.AppIcon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

private class DiffApplication : ApplicationStarterBase(/* ...possibleArgumentsCount = */ 0, 2, 3) {
  override val usageMessage: String
    get() {
      val scriptName = ApplicationNamesInfo.getInstance().scriptName
      return DiffBundle.message("diff.application.usage.parameters.and.description", scriptName)
    }

  override suspend fun executeCommand(args: List<String>, currentDirectory: String?): CliResult {
    val filePaths = args.drop(1).filter { it != "--wait" }
    val files = findFilesOrThrow(filePaths, currentDirectory)
    val project = guessProject(files)
    return withContext(Dispatchers.EDT) {
      val chain: DiffRequestChain
      if (files.isEmpty()) {
        chain = createBlankDiffRequestChain(project)
        setupBlankContext(chain)
      }
      else {
        chain = SimpleDiffRequestChain.fromProducer(MyDiffRequestProducer(project, files))
        chain.putUserData(DiffUserDataKeys.PLACE, DiffPlaces.EXTERNAL)
      }
      val mode = if (project != null) WindowWrapper.Mode.FRAME else WindowWrapper.Mode.MODAL
      val task = CompletableDeferred<Unit>()
      val dialogHints = DiffDialogHints(mode, null) { wrapper ->
        val window = wrapper.window
        hideSplashBeforeShow(window)
        AppIcon.getInstance().requestFocus(window)
        window.addWindowListener(object : WindowAdapter() {
          override fun windowClosed(e: WindowEvent) {
            try {
              e.window.removeWindowListener(this)
              for (file in files) {
                saveIfNeeded(file)
              }
            }
            finally {
              task.complete(Unit)
            }
          }
        })
      }
      DiffManagerEx.getInstance().showDiffBuiltin(project, chain, dialogHints)
      task.await()
      return@withContext CliResult.OK
    }
  }
}

private class MyDiffRequestProducer(private val project: Project?, private val files: List<VirtualFile?>) : DiffRequestProducer {
  override fun getName(): String {
    return if (files.size == 3) {
      val base = files[2] ?: return DiffBundle.message("diff.files.dialog.title")
      DiffRequestFactory.getInstance().getTitle(base)
    }
    else {
      DiffRequestFactory.getInstance().getTitleForComparison(files[0], files[1])
    }
  }

  override fun getContentType(): FileType? {
    return files.firstOrNull { it != null }?.fileType
  }

  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    return if (files.size == 3) {
      val nonNullFiles = DiffApplicationBase.replaceNullsWithEmptyFile(files)
      DiffRequestFactory.getInstance().createFromFiles(project, nonNullFiles[0], nonNullFiles[2], nonNullFiles[1])
    }
    else {
      DiffRequestFactory.getInstance().createFromFiles(project, files[0], files[1])
    }
  }
}
