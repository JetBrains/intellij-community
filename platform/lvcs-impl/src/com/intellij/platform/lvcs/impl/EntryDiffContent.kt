// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContentFactoryEx
import com.intellij.diff.contents.DiffContent
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project

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
  if (document == null) return DiffContentFactory.getInstance().create(LocalHistoryBundle.message("content.not.available"))
  return DiffContentFactory.getInstance().create(project, document)
}