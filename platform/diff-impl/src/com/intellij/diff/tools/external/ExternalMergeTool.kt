// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.external

import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.merge.ThreesideMergeRequest
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalTool
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ExternalMergeTool {
  private val LOG = Logger.getInstance(ExternalMergeTool::class.java)

  @JvmStatic
  fun isEnabled(): Boolean {
    return ExternalDiffSettings.instance.isExternalToolsEnabled &&
           ExternalDiffSettings.instance.externalTools[ExternalToolGroup.MERGE_TOOL].orEmpty().isNotEmpty()
  }

  @JvmStatic
  fun isDefault(): Boolean = isEnabled() && ExternalDiffSettings.isNotBuiltinMergeTool()

  @JvmStatic
  fun show(project: Project?, externalTool: ExternalTool, request: ThreesideMergeRequest) {
    try {
      ExternalDiffToolUtil.executeMerge(project, externalTool, request, null)
    }
    catch (_: ProcessCanceledException) {
    }
    catch (e: Throwable) {
      LOG.warn(e)
      Messages.showErrorDialog(project, e.message, DiffBundle.message("can.t.show.merge.in.external.tool"))
    }
  }

  fun canShow(request: ThreesideMergeRequest): Boolean {
    return request.outputContent.canProcessOutputContent() &&
           request.contents.size == 3 &&
           request.contents.all { content -> ExternalDiffToolUtil.canCreateFile(content) }
  }

  private fun DiffContent.canProcessOutputContent(): Boolean {
    return when (this) {
      is DocumentContent -> true
      is FileContent if file.isInLocalFileSystem -> true
      else -> false
    }
  }
}
