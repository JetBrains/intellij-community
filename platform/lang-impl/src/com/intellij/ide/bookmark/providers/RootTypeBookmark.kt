// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchTreeStructureProvider
import java.util.Objects

internal class RootTypeBookmark(override val provider: RootTypeBookmarkProvider, val type: RootType) : Bookmark {

  val file
    get() = ScratchTreeStructureProvider.getVirtualFile(type)

  override val attributes: Map<String, String>
    get() = mapOf("root.type.id" to type.id)

  override fun createNode() = RootTypeNode(provider.project, this)

  override fun canNavigate() = !provider.project.isDisposed && file?.isValid == true
  override fun canNavigateToSource() = false
  override fun navigate(requestFocus: Boolean) {
    val context = file?.let { FileSelectInContext(provider.project, it, null) } ?: return
    SelectInManager.getInstance(provider.project).targetList.find { context.selectIn(it, requestFocus) }
  }

  override fun hashCode() = Objects.hash(provider, type)
  override fun equals(other: Any?) = other === this || other is RootTypeBookmark
                                     && other.provider == provider
                                     && other.type == type

  override fun toString() = "ScratchBookmark(module=${type.id},provider=$provider)"
}
