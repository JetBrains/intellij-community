// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointer

open class LightFilePointer : VirtualFilePointer {
  private val myUrl: String

  @Volatile
  private var myFile: VirtualFile? = null

  @Volatile
  private var isRefreshed = false

  constructor(url: String) {
    myUrl = url
  }

  constructor(file: VirtualFile) {
    myUrl = file.url
    myFile = file
  }

  override fun getFile(): VirtualFile? {
    refreshFile()
    return myFile
  }

  override fun getUrl(): String = myUrl

  override fun getFileName(): String {
    myFile?.let {
      return it.name
    }

    val index = myUrl.lastIndexOf('/')
    return if (index >= 0) myUrl.substring(index + 1) else myUrl
  }

  override fun getPresentableUrl(): String {
    return file?.presentableUrl ?: toPresentableUrl(myUrl)
  }

  override fun isValid(): Boolean = file != null

  private fun refreshFile() {
    val file = myFile
    if (file != null && file.isValid) {
      return
    }

    val virtualFileManager = VirtualFileManager.getInstance()
    var virtualFile = virtualFileManager.findFileByUrl(myUrl)
    if (virtualFile == null && !isRefreshed) {
      isRefreshed = true
      val app = ApplicationManager.getApplication()
      if (app.isDispatchThread || !app.isReadAccessAllowed) {
        virtualFile = virtualFileManager.refreshAndFindFileByUrl(myUrl)
      }
      else {
        app.executeOnPooledThread { virtualFileManager.refreshAndFindFileByUrl(myUrl) }
      }
    }
    myFile = if (virtualFile != null && virtualFile.isValid) virtualFile else null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return if (other is LightFilePointer) myUrl == other.myUrl else false
  }

  override fun hashCode(): Int = myUrl.hashCode()
}

private fun toPresentableUrl(url: String): String {
  val fileSystem = VirtualFileManager.getInstance().getFileSystem(VirtualFileManager.extractProtocol(url))
  return (fileSystem ?: StandardFileSystems.local()).extractPresentableUrl(VirtualFileManager.extractPath(url))
}