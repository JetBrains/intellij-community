// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContentFactoryEx
import com.intellij.diff.contents.DiffContent
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.history.integration.ui.models.RevisionProcessingProgress
import com.intellij.history.integration.ui.models.SelectionCalculator
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lvcs.impl.RevisionId

fun createDiffContent(project: Project?, gateway: IdeaGateway, e: Entry): DiffContent {
  val content = e.content.getBytes()
  val virtualFile = gateway.findVirtualFile(e.getPath())
  if (virtualFile != null) {
    return DiffContentFactoryEx.getInstanceEx().createDocumentFromBytes(project, content, virtualFile)
  }
  val fileType = gateway.getFileType(e.getName())
  return DiffContentFactoryEx.getInstanceEx().createDocumentFromBytes(project, content, fileType, e.getName())
}

fun createCurrentDiffContent(project: Project?, gateway: IdeaGateway, path: String): DiffContent {
  val document = runReadAction { gateway.getDocument(path) }
  if (document == null) return createUnavailableContent()
  return DiffContentFactory.getInstance().create(project, document)
}

fun createDiffContent(gateway: IdeaGateway,
                      entry: Entry,
                      changeSetId: Long,
                      calculator: SelectionCalculator,
                      progress: RevisionProcessingProgress): DiffContent {
  val content = calculator.getSelectionFor(RevisionId.ChangeSet(changeSetId), progress).blockContent
  val virtualFile = gateway.findVirtualFile(entry.path)
  if (virtualFile != null) {
    return DiffContentFactory.getInstance().create(content, virtualFile)
  }
  val fileType = gateway.getFileType(entry.name)
  return DiffContentFactory.getInstance().create(content, fileType)
}

fun createCurrentDiffContent(project: Project?, gateway: IdeaGateway, path: String, from: Int, to: Int): DiffContent {
  return runReadAction {
    val document = gateway.getDocument(path)
    if (document == null) return@runReadAction createUnavailableContent()

    val fromOffset = document.getLineStartOffset(from)
    val toOffset = document.getLineEndOffset(to)

    return@runReadAction DiffContentFactory.getInstance().createFragment(project, document, TextRange(fromOffset, toOffset))
  }
}

private fun createUnavailableContent() = DiffContentFactory.getInstance().create(LocalHistoryBundle.message("content.not.available"))
