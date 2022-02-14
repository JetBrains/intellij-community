// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.scratch.RootType
import java.util.Objects

internal class RootTypeBookmark(override val provider: RootTypeBookmarkProvider, val type: RootType) : Bookmark {

  override val attributes: Map<String, String>
    get() = mapOf("root.type.id" to type.id)

  override fun createNode() = RootTypeNode(provider.project, this)

  override fun canNavigate() = false
  override fun canNavigateToSource() = false
  override fun navigate(requestFocus: Boolean) = Unit

  override fun hashCode() = Objects.hash(provider, type)
  override fun equals(other: Any?) = other === this || other is RootTypeBookmark
                                     && other.provider == provider
                                     && other.type == type

  override fun toString() = "ScratchBookmark(module=${type.id},provider=$provider)"
}
