// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.ListCellRenderer

@ApiStatus.Internal
class ScopeSeparator @ApiStatus.Internal constructor(@Nls val text: String) : ScopeDescriptor(null) {

  override fun getDisplayName(): String {
    return text
  }
}

internal fun createScopeDescriptorRenderer(scopesSupplier: Supplier<ScopesSnapshot?>): ListCellRenderer<ScopeDescriptor?> {
  return listCellRenderer("") {
    value.icon?.let {
      icon(it)
    }
    text(value.displayName ?: "")

    val scopes = scopesSupplier.get()
    scopes?.getSeparatorFor(value)?.let {
      separator {
        text = it.text
      }
    }
  }
}