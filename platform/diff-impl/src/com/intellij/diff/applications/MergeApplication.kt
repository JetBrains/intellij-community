// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.applications

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.merge.MergeRequestProducer
import com.intellij.diff.merge.MergeResult
import com.intellij.ide.CliResult
import com.intellij.idea.SplashManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ApplicationStarterBase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AppIcon
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

internal class MergeApplication : ApplicationStarterBase(3, 4) {
  override val commandName: String
    get() = "merge"

  override val usageMessage: String
    get() {
      val scriptName = ApplicationNamesInfo.getInstance().scriptName
      return DiffBundle.message("merge.application.usage.parameters.and.description", scriptName)
    }

  override suspend fun executeCommand(args: List<String>, currentDirectory: String?): CliResult {
    val filePaths = args.subList(1, 4)
    val files = DiffApplicationBase.findFilesOrThrow(filePaths, currentDirectory)
    val project = DiffApplicationBase.guessProject(files)
    val contents = listOf(files[0], files[2], files[1]) // left, base, right
    val outputFile: VirtualFile?
    if (args.size == 5) {
      val outputFilePath = args[4]
      outputFile = DiffApplicationBase.findOrCreateFile(outputFilePath, currentDirectory)
      DiffApplicationBase.refreshAndEnsureFilesValid(listOf(outputFile))
    }
    else {
      outputFile = files[2] // base
    }
    if (outputFile == null) {
      throw Exception(DiffBundle.message("cannot.create.file.error", ContainerUtil.getLastItem(filePaths)))
    }

    val deferred = CompletableDeferred<CliResult>()
    withContext(Dispatchers.EDT) {
      val resultRef = AtomicReference(CliResult(127, null))
      val requestProducer = MyMergeRequestProducer(project, outputFile, contents, resultRef)
      val mode = if (project == null) WindowWrapper.Mode.MODAL else WindowWrapper.Mode.FRAME
      val dialogHints = DiffDialogHints(mode, null) { wrapper ->
        val window = wrapper.window
        SplashManager.hideBeforeShow(window)
        AppIcon.getInstance().requestFocus(window)
        UIUtil.runWhenWindowClosed(window) { deferred.complete(resultRef.get()) }
      }
      DiffManagerEx.getInstance().showMergeBuiltin(project, requestProducer, dialogHints)
    }
    return deferred.await()
  }

  private class MyMergeRequestProducer(private val project: Project?,
                                       private val outputFile: VirtualFile,
                                       private val contents: List<VirtualFile>,
                                       private val resultRef: AtomicReference<CliResult>) : MergeRequestProducer {
    override fun getName(): String = DiffBundle.message("merge.window.title.file", outputFile.presentableUrl)

    override fun process(context: UserDataHolder, indicator: ProgressIndicator): MergeRequest {
      return try {
        val contents = DiffApplicationBase.replaceNullsWithEmptyFile(contents)
        DiffRequestFactory.getInstance().createMergeRequestFromFiles(project, outputFile, contents) { result ->
          try {
            saveIfNeeded(outputFile)
          }
          finally {
            resultRef.set(CliResult(if (result == MergeResult.CANCEL) 1 else 0, null))
          }
        }
      }
      catch (e: Throwable) {
        resultRef.set(CliResult(127, e.message))
        throw DiffRequestProducerException(e)
      }
    }
  }
}