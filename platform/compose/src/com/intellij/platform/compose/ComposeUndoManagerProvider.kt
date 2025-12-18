// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.command.undo.UndoManagerProvider
import com.intellij.openapi.command.undo.UndoManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.ComposeSemanticsTreeUtils.findFocusedComponent
import org.jetbrains.jewel.bridge.ComposeSemanticsTreeUtils.isEditableTextField
import org.jetbrains.jewel.foundation.InternalJewelApi

@ApiStatus.Internal
internal class ComposeUndoManagerProvider : UndoManagerProvider {
  @OptIn(InternalJewelApi::class)
  override fun getUndoManager(
    dataContext: DataContext,
  ): UndoManager? {
    val component = dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val parent = component?.parent as? ComposePanel
    val focused = parent?.findFocusedComponent()
    if (focused != null && focused.isEditableTextField()) {
      return ComposeUndoManager(component)
    }
    return null
  }
}
