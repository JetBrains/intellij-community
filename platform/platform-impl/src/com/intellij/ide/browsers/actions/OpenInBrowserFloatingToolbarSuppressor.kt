// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.OverrideOnly

@Internal
@OverrideOnly
interface OpenInBrowserFloatingToolbarSuppressor {

  /**
   * Determines if the browser floating toolbar should be suppressed.
   */
  suspend fun isSuppressed(provider: FloatingToolbarProvider, dataContext: DataContext): Boolean

  companion object {

    internal val EP_NAME: ExtensionPointName<OpenInBrowserFloatingToolbarSuppressor> =
      create("com.intellij.openInBrowserFloatingToolbarSuppressor")
  }
}
