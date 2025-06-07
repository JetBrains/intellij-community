// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

internal class RootTypeBookmark(override val provider: RootTypeBookmarkProvider, val type: RootType) : Bookmark {

  val file: VirtualFile?
    get() = ScratchFileService.getInstance().getVirtualFile(type)

  override val attributes: Map<String, String>
    get() = mapOf("root.type.id" to type.id)

  override fun createNode(): RootTypeNode = RootTypeNode(provider.project, this)

  override fun canNavigate(): Boolean = !provider.project.isDisposed && file?.isValid == true
  override fun canNavigateToSource(): Boolean = false
  override fun navigate(requestFocus: Boolean) {
    val context = file?.let { FileSelectInContext(provider.project, it, null) } ?: return
    SelectInManager.getInstance(provider.project).targetList.find { context.selectIn(it, requestFocus) }
  }

  override fun hashCode(): Int = Objects.hash(provider, type)
  override fun equals(other: Any?): Boolean = other === this || other is RootTypeBookmark
                                              && other.provider == provider
                                              && other.type == type

  override fun toString(): String = "ScratchBookmark(module=${type.id},provider=$provider)"
}
