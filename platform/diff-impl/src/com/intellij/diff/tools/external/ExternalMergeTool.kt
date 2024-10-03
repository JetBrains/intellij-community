// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.external

import com.intellij.diff.DiffManagerEx
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.merge.MergeRequest
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
  fun show(project: Project?,
           externalTool: ExternalTool,
           request: MergeRequest) {
    try {
      if (canShow(request)) {
        ExternalDiffToolUtil.executeMerge(project, externalTool, (request as ThreesideMergeRequest), null)
      }
      else {
        DiffManagerEx.getInstance().showMergeBuiltin(project, request)
      }
    }
    catch (ignore: ProcessCanceledException) {
    }
    catch (e: Throwable) {
      LOG.warn(e)
      Messages.showErrorDialog(project, e.message, DiffBundle.message("can.t.show.merge.in.external.tool"))
    }
  }

  @JvmStatic
  fun canShow(request: MergeRequest): Boolean {
    if (request is ThreesideMergeRequest) {
      val outputContent = request.outputContent
      if (!canProcessOutputContent(outputContent)) return false

      val contents = request.contents
      if (contents.size != 3) return false
      for (content in contents) {
        if (!ExternalDiffToolUtil.canCreateFile(content)) return false
      }
      return true
    }
    return false
  }

  private fun canProcessOutputContent(content: DiffContent): Boolean {
    if (content is DocumentContent) return true
    if (content is FileContent && content.file.isInLocalFileSystem) return true
    return false
  }
}
