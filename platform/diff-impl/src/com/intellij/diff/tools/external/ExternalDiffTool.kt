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
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.util.ThrowableConvertor
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.io.IOException

object ExternalDiffTool {
  private val LOG = Logger.getInstance(ExternalDiffTool::class.java)
  private val ERROR_NOTIFICATION_GROUP_ID = "Diff Changes Loading Error"

  @JvmStatic
  fun isEnabled(): Boolean {
    return ExternalDiffSettings.instance.isExternalToolsEnabled
  }

  @JvmStatic
  fun isDefault(): Boolean = isEnabled() && ExternalDiffSettings.isNotBuiltinDiffTool()

  @JvmStatic
  fun wantShowExternalToolFor(diffProducers: List<DiffRequestProducer>): Boolean {
    if (isDefault()) return true

    return diffProducers.asSequence()
      .map { producer -> getFileType(producer) }
      .distinct()
      .mapNotNull { fileType -> ExternalDiffSettings.findDiffTool(fileType) }
      .firstOrNull() != null
  }

  private fun getFileType(producer: DiffRequestProducer): FileType {
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

  private fun getExternalToolFor(request: ContentDiffRequest): ExternalTool? {
    val diffTool = request.contents.asSequence()
      .mapNotNull { content -> content.contentType }
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
      return show(project, hints) { indicator: ProgressIndicator ->
        val listSelection = chain.loadRequestsInBackground()
        val producers = listSelection.explicitSelection
        if (!wantShowExternalToolFor(producers)) return@show null
        if (!checkNotTooManyRequests(project, producers)) return@show null
        collectRequests(project, producers, indicator)
      }
    }
    else {
      val listSelection = when (chain) {
        is DiffRequestSelectionChain -> chain.listSelection
        else -> ListSelection.createAt(chain.requests, chain.index)
      }

      return show(project, hints) { indicator: ProgressIndicator ->
        val producers = listSelection.explicitSelection
        if (!wantShowExternalToolFor(producers)) return@show null
        if (!checkNotTooManyRequests(project, producers)) return@show null
        collectRequests(project, producers, indicator)
      }
    }
  }

  @JvmStatic
  fun showIfNeeded(project: Project?,
                   requestProducers: List<DiffRequestProducer>,
                   hints: DiffDialogHints): Boolean {
    return show(project, hints) { indicator: ProgressIndicator ->
      if (!wantShowExternalToolFor(requestProducers)) return@show null
      if (!checkNotTooManyRequests(project, requestProducers)) return@show null
      collectRequests(project, requestProducers, indicator)
    }
  }

  private fun show(project: Project?,
                   hints: DiffDialogHints,
                   requestsProducer: ThrowableConvertor<in ProgressIndicator, out List<DiffRequest>?, out Exception>): Boolean {
    try {
      val requests: List<DiffRequest>? = computeWithModalProgress(project,
                                                                  DiffBundle.message("progress.title.loading.requests"),
                                                                  requestsProducer)
      if (requests == null) return false

      showRequests(project, requests, hints)
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

  @RequiresEdt
  @Throws(IOException::class)
  private fun showRequests(project: Project?,
                           requests: List<DiffRequest>,
                           hints: DiffDialogHints) {
    val showInBuiltin = mutableListOf<DiffRequest>()
    for (request in requests) {
      val success = tryShowRequestInExternal(project, request)
      if (!success) {
        showInBuiltin.add(request)
      }
    }

    if (!showInBuiltin.isEmpty()) {
      DiffManagerEx.getInstance().showDiffBuiltin(project, SimpleDiffRequestChain(showInBuiltin), hints)
    }
  }

  @Throws(IOException::class)
  private fun tryShowRequestInExternal(project: Project?, request: DiffRequest): Boolean {
    if (!canShow(request)) return false

    val externalTool = getExternalToolFor((request as ContentDiffRequest))
    if (externalTool == null) return false

    showRequest(project, request, externalTool)
    return true
  }

  private fun collectRequests(project: Project?,
                              producers: List<DiffRequestProducer>,
                              indicator: ProgressIndicator): List<DiffRequest> {
    val requests = mutableListOf<DiffRequest>()

    val context = UserDataHolderBase()
    val errorRequests = mutableListOf<DiffRequestProducer>()

    for (producer in producers) {
      try {
        requests.add(producer.process(context, indicator))
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

    return requests
  }

  @Throws(Exception::class)
  private fun <T> computeWithModalProgress(project: Project?,
                                           title: @NlsContexts.DialogTitle String,
                                           computable: ThrowableConvertor<in ProgressIndicator, T, out Exception>): T {
    return ProgressManager.getInstance().run(object : Task.WithResult<T, Exception>(project, title, true) {
      @Throws(Exception::class)
      override fun compute(indicator: ProgressIndicator): T {
        return computable.convert(indicator)
      }
    })
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
}
