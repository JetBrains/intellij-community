// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.projectView.impl.nodes.PackageElement
import java.util.Objects

internal class PackageBookmark(override val provider: PackageBookmarkProvider, val element: PackageElement) : Bookmark {

  internal val name
    get() = element.`package`.qualifiedName

  internal val module
    get() = element.module?.name

  internal val library
    get() = element.isLibraryElement

  override val attributes: Map<String, String>
    get() = module?.let { mapOf("package" to name, "module" to it, "library" to library.toString()) }
            ?: mapOf("package" to name, "library" to library.toString())

  override fun createNode() = PackageNode(provider.project, this)

  override fun canNavigate() = element.`package`.canNavigate()
  override fun canNavigateToSource() = element.`package`.canNavigateToSource()
  override fun navigate(requestFocus: Boolean) = element.`package`.navigate(requestFocus)

  override fun hashCode() = Objects.hash(provider, element)
  override fun equals(other: Any?) = other === this || other is PackageBookmark
                                     && other.provider == provider
                                     && other.element == element

  override fun toString() = "PackageBookmark(package=$name,module=$module,library=$library,provider=$provider)"
}
