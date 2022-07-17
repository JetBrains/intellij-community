// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.Bookmark
import org.jetbrains.annotations.Nls
import java.util.Objects

internal class ModuleBookmark(override val provider: ModuleBookmarkProvider, val name: @Nls String, val isGroup: Boolean) : Bookmark {

  override val attributes: Map<String, String>
    get() = when (isGroup) {
      true -> mapOf("group" to name)
      else -> mapOf("module" to name)
    }

  override fun createNode() = ModuleNode(provider.project, this)

  override fun canNavigate() = provider.projectSettingsService != null && provider.moduleManager != null
  override fun canNavigateToSource() = false
  override fun navigate(requestFocus: Boolean) {
    val service = provider.projectSettingsService ?: return
    val module = provider.moduleManager?.findModuleByName(name)
    when (module != null && service.canOpenModuleSettings()) {
      true -> service.openModuleSettings(module)
      else -> service.openProjectSettings()
    }
  }

  override fun hashCode() = Objects.hash(provider, name, isGroup)
  override fun equals(other: Any?) = other === this || other is ModuleBookmark
                                     && other.provider == provider
                                     && other.isGroup == isGroup
                                     && other.name == name

  override fun toString() = when (isGroup) {
    true -> "ModuleBookmark(group=$name,provider=$provider)"
    else -> "ModuleBookmark(module=$name,provider=$provider)"
  }
}
