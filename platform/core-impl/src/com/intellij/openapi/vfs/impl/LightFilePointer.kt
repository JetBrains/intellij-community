// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointer

open class LightFilePointer : VirtualFilePointer {
  private val url: String

  @Volatile
  private var cachedFile: VirtualFile? = null

  @Volatile
  private var isRefreshed = false

  constructor(url: String) {
    this.url = url
  }

  constructor(file: VirtualFile) {
    url = file.url
    cachedFile = file
  }

  override fun getFile(): VirtualFile? {
    refreshFile()
    return cachedFile
  }

  override fun getUrl(): String = url

  override fun getFileName(): String {
    cachedFile?.let {
      return it.name
    }

    val index = url.lastIndexOf('/')
    return if (index >= 0) url.substring(index + 1) else url
  }

  override fun getPresentableUrl(): String {
    return file?.presentableUrl ?: toPresentableUrl(url)
  }

  override fun isValid(): Boolean = file != null

  private fun refreshFile() {
    cachedFile?.let {
      if (it.isValid) {
        return
      }
    }

    val virtualFileManager = VirtualFileManager.getInstance()
    var virtualFile = virtualFileManager.findFileByUrl(url)
    if (virtualFile == null && !isRefreshed) {
      isRefreshed = true
      val app = ApplicationManager.getApplication()
      if (app.isDispatchThread || !app.isReadAccessAllowed) {
        virtualFile = virtualFileManager.refreshAndFindFileByUrl(url)
      }
      else {
        app.executeOnPooledThread { virtualFileManager.refreshAndFindFileByUrl(url) }
      }
    }
    cachedFile = if (virtualFile != null && virtualFile.isValid) virtualFile else null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return if (other is LightFilePointer) url == other.url else false
  }

  override fun hashCode(): Int = url.hashCode()
}

private fun toPresentableUrl(url: String): String {
  val fileSystem = VirtualFileManager.getInstance().getFileSystem(VirtualFileManager.extractProtocol(url))
  return (fileSystem ?: StandardFileSystems.local()).extractPresentableUrl(VirtualFileManager.extractPath(url))
}