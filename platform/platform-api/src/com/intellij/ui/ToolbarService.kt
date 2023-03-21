// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import java.awt.Window
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JRootPane

interface ToolbarService {
  companion object {
    @JvmStatic
    val instance: ToolbarService
      get() = ApplicationManager.getApplication().service()
  }

  fun setTransparentTitleBar(window: Window,
                             rootPane: JRootPane,
                             onDispose: Consumer<in Runnable?>) {
    setTransparentTitleBar(window, rootPane, null, onDispose)
  }

  fun setTransparentTitleBar(window: Window,
                             rootPane: JRootPane,
                             handlerProvider: Supplier<out FullScreenSupport>?,
                             onDispose: Consumer<in Runnable>)

  fun setCustomTitleBar(window: Window,
                        rootPane: JRootPane,
                        onDispose: Consumer<in Runnable?>)

}