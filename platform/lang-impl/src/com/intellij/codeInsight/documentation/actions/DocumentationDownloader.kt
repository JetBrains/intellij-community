// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.actions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface DocumentationDownloader {

  suspend fun canHandle(project: Project, file: VirtualFile): Boolean

  suspend fun download(project: Project, file: VirtualFile): Boolean

  companion object {
    val EP = ExtensionPointName.create<DocumentationDownloader>("com.intellij.documentation.documentationDownloader")
    const val HREF_PREFIX = "download_sources:"

    fun formatLink(target: VirtualFile?): String? {
      val virtualFileUrl: String = target?.url ?: return null
      return HREF_PREFIX + virtualFileUrl
    }
  }
}