// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import java.awt.Window
import javax.swing.JRootPane

interface ToolbarService {
  companion object {
    fun getInstance(): ToolbarService = ApplicationManager.getApplication().service()
  }

  fun setTransparentTitleBar(window: Window, rootPane: JRootPane, onDispose: (Runnable) -> Unit) {
    setTransparentTitleBar(window = window, rootPane = rootPane, handlerProvider = null, onDispose = onDispose)
  }

  fun setTransparentTitleBar(window: Window,
                             rootPane: JRootPane,
                             handlerProvider: (() -> FullScreenSupport)?,
                             onDispose: (Runnable) -> Unit)

  fun setCustomTitleBar(window: Window, rootPane: JRootPane, onDispose: (Runnable) -> Unit)
}