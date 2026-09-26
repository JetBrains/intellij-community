// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import com.intellij.openapi.extensions.forEachExtensionSafeInline
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class OpenInBrowserFloatingToolbarProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {

  override suspend fun isApplicableAsync(dataContext: DataContext): Boolean {
    OpenInBrowserFloatingToolbarSuppressor.EP_NAME.forEachExtensionSafeInline {
      if (it.isSuppressed(this, dataContext)) {
        return false
      }
    }
    return super.isApplicableAsync(dataContext)
  }

  companion object {
    private const val ACTION_GROUP = "OpenInBrowserEditorContextBarGroup"
  }
}
