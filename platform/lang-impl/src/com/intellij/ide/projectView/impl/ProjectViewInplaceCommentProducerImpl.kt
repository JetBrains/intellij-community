// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.InplaceCommentAppender
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.text.JBDateFormat

internal fun appendInplaceComments(node: ProjectViewNode<*>, appender: InplaceCommentAppender) {
  val parentNode = node.parent
  val content = node.value
  if (content is PsiFileSystemItem || content !is PsiElement || parentNode != null && parentNode.value is PsiDirectory) {
    appendInplaceComments(appender, node.project, node.virtualFile)
  }
}

// To be used in Rider once it migrates from legacy logic, don't change the signature and/or visibility.
fun appendInplaceComments(appender: InplaceCommentAppender, project: Project?, file: VirtualFile?) {
  val fileAttributes = getFileAttributes(file)
  if (fileAttributes != null) {
    appender.append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    val attributes = SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
    appender.append(JBDateFormat.getFormatter().formatDateTime(fileAttributes.lastModifiedTime().toMillis()), attributes)
    appender.append(", " + StringUtil.formatFileSize(fileAttributes.size()), attributes)
  }

  if (Registry.`is`("show.last.visited.timestamps") && file != null && project != null) {
    IdeDocumentHistoryImpl.appendTimestamp(project, appender, file)
  }
}
