// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.ListCellRenderer

@ApiStatus.Internal
class ScopeSeparator @ApiStatus.Internal constructor(@Nls val text: String) : ScopeDescriptor(null) {

  override fun getDisplayName(): String {
    return text
  }
}

internal fun createScopeDescriptorRenderer(separatorProvider: ((ScopeDescriptor) -> ListSeparator?)?, @Nls nullText: String? = null): ListCellRenderer<ScopeDescriptor?> {
  return listCellRenderer(nullText ?: "") {
    value.icon?.let {
      icon(it)
    }
    text(value.displayName ?: "")

    separatorProvider?.invoke(value)?.let {
      separator {
        text = it.text
      }
    }
  }
}