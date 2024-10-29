// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.external

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffNotificationIdsHolder
import com.intellij.diff.chains.*
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalTool
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolGroup
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.ListSelection
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Internal
object ExternalDiffTool {
  private val LOG = Logger.getInstance(ExternalDiffTool::class.java)
  private val ERROR_NOTIFICATION_GROUP_ID = "Diff Changes Loading Error"

  @JvmStatic
  fun isEnabled(): Boolean {
    return ExternalDiffSettings.instance.isExternalToolsEnabled &&
           ExternalDiffSettings.instance.externalTools[ExternalToolGroup.DIFF_TOOL].orEmpty().isNotEmpty()
  }

  @JvmStatic
  fun isDefault(): Boolean = isEnabled() && ExternalDiffSettings.isNotBuiltinDiffTool()

  @JvmStatic
  fun wantShowExternalToolFor(diffProducers: List<DiffRequestProducer>): Boolean {
    if (isDefault()) return true

    return diffProducers.asSequence()
      .map { producer -> getProducerFileType(producer) }
      .distinct()
      .mapNotNull { fileType -> ExternalDiffSettings.findDiffTool(fileType) }
      .firstOrNull() != null
  }

  private fun getProducerFileType(producer: DiffRequestProducer): FileType {
    val contentType = producer.contentType
    if (contentType != null) return contentType

    val filePath = producer.name
    return FileTypeManager.getInstance().getFileTypeByFileName(filePath)
  }

  @JvmStatic
  fun checkNotTooManyRequests(project: Project?, diffProducers: List<DiffRequestProducer>): Boolean {
    if (diffProducers.size <= Registry.intValue("diff.external.tool.file.limit")) return true
    Notification(ERROR_NOTIFICATION_GROUP_ID,
                 DiffBundle.message("can.t.show.diff.in.external.tool.too.many.files", diffProducers.size),
                 NotificationType.WARNING)
      .setDisplayId(DiffNotificationIdsHolder.EXTERNAL_TOO_MANY_SELECTED)
      .notify(project)
    return false
  }

  private fun getExternalToolFor(producer: DiffRequestProducer, request: DiffRequest): ExternalTool? {
    if (request !is ContentDiffRequest) return null
    if (!canShow(request)) return null

    val fileTypes: List<FileType?> = request.contents.map { content -> content.contentType } +
                                     getProducerFileType(producer) // Fix disappearing 'Unknown' type after automatic detection as 'PlainText'

    val diffTool = fileTypes.asSequence()
      .filterNotNull()
      .distinct()
      .sortedBy { fileType -> if (fileType !== UnknownFileType.INSTANCE) -1 else 1 }
      .mapNotNull { fileType -> ExternalDiffSettings.findDiffTool(fileType) }
      .firstOrNull()
    if (diffTool != null) return diffTool

    if (isDefault()) {
      return ExternalDiffSettings.findDefaultDiffTool()
    }
    return null
  }

  @JvmStatic
  @RequiresEdt
  fun showIfNeeded(project: Project?,
                   chain: DiffRequestChain,
                   hints: DiffDialogHints): Boolean {
    if (chain is AsyncDiffRequestChain) {
      return showIfNeeded(project, hints) {
        chain.loadRequestsInBackground().explicitSelection
      }
    }
    else {
      val listSelection = when (chain) {
        is DiffRequestSelectionChain -> chain.listSelection
        else -> ListSelection.createAt(chain.requests, chain.index)
      }

      return showIfNeeded(project, hints) {
        listSelection.explicitSelection
      }
    }
  }

  @JvmStatic
  fun showIfNeeded(project: Project?,
                   requestProducers: List<DiffRequestProducer>,
                   hints: DiffDialogHints): Boolean {
    return showIfNeeded(project, hints) {
      requestProducers
    }
  }

  private fun showIfNeeded(project: Project?,
                           hints: DiffDialogHints,
                           requestProducer: () -> List<DiffRequestProducer>): Boolean {
    try {
      val requestsByTool = runWithModalProgressBlocking(
        owner = if (project != null) ModalTaskOwner.project(project) else ModalTaskOwner.guess(),
        title = DiffBundle.message("progress.title.loading.requests")
      ) {
        coroutineToIndicator {
          val requestProducers = requestProducer()
          collectRequestsForExternalTool(project, requestProducers)
        }
      }
      if (requestsByTool == null) return false

      if (requestsByTool.showByExternalTool.isEmpty()) {
        return false // do not show all in built-in ourselves, let the caller handle the request (ex: show diff-preview)
      }

      for ((externalTool, requests) in requestsByTool.showByExternalTool) {
        for (request in requests) {
          showRequest(project, request, externalTool)
        }
      }

      if (!requestsByTool.showInBuiltin.isEmpty()) {
        DiffManagerEx.getInstance().showDiffBuiltin(project, SimpleDiffRequestChain(requestsByTool.showInBuiltin), hints)
      }

      return true
    }
    catch (ignore: ProcessCanceledException) {
    }
    catch (e: Throwable) {
      LOG.warn(e)
      Messages.showErrorDialog(project, e.message, DiffBundle.message("can.t.show.diff.in.external.tool"))
    }
    return false
  }

  private fun collectRequestsForExternalTool(project: Project?,
                                             producers: List<DiffRequestProducer>): RequestsByTool? {
    if (!wantShowExternalToolFor(producers)) return null
    if (!checkNotTooManyRequests(project, producers)) return null

    val showInBuiltin = mutableListOf<DiffRequest>()
    val showByExternalTool = mutableMapOf<ExternalTool, MutableList<DiffRequest>>()

    val context = UserDataHolderBase()
    val errorRequests = mutableListOf<DiffRequestProducer>()

    val indicator = ProgressManager.getGlobalProgressIndicator()
    for (producer in producers) {
      try {
        val request = producer.process(context, indicator)
        val externalTool = getExternalToolFor(producer, request)
        if (externalTool != null) {
          val toolRequests = showByExternalTool.computeIfAbsent(externalTool) { mutableListOf() }
          toolRequests.add(request)
        }
        else {
          showInBuiltin.add(request)
        }
      }
      catch (e: DiffRequestProducerException) {
        LOG.warn(e)
        errorRequests.add(producer)
      }
    }

    if (!errorRequests.isEmpty()) {
      val message = HtmlBuilder()
        .appendWithSeparators(HtmlChunk.br(), errorRequests.map { producer -> HtmlChunk.text(producer.name) })
      Notification(ERROR_NOTIFICATION_GROUP_ID,
                   DiffBundle.message("can.t.load.some.changes"),
                   message.toString(),
                   NotificationType.ERROR)
        .setDisplayId(DiffNotificationIdsHolder.EXTERNAL_CANT_LOAD_CHANGES)
        .notify(project)
    }

    return RequestsByTool(showByExternalTool, showInBuiltin)
  }

  @JvmStatic
  @Throws(IOException::class)
  fun showRequest(project: Project?,
                  request: DiffRequest,
                  externalDiffTool: ExternalTool) {
    request as ContentDiffRequest

    request.onAssigned(true)
    try {
      val contents = request.contents
      val titles = request.contentTitles
      ExternalDiffToolUtil.executeDiff(project, externalDiffTool, contents, titles, request.getTitle())
    }
    finally {
      request.onAssigned(false)
    }
  }

  @JvmStatic
  fun canShow(request: DiffRequest): Boolean {
    if (request !is ContentDiffRequest) return false
    val contents = request.contents
    if (contents.size != 2 && contents.size != 3) return false
    for (content in contents) {
      if (!ExternalDiffToolUtil.canCreateFile(content)) return false
    }
    return true
  }

  private class RequestsByTool(
    val showByExternalTool: Map<ExternalTool, List<DiffRequest>>,
    val showInBuiltin: List<DiffRequest>
  )
}
