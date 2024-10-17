// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.compose

import androidx.compose.runtime.Composable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.jewel.bridge.JewelComposePanel
import javax.swing.JComponent

@Suppress("FunctionName")
@Experimental
fun JBComposePanel(
  content: @Composable () -> Unit
): JComponent {
  if (ApplicationManager.getApplication().isInternal) {
    System.setProperty("compose.swing.render.on.graphics", Registry.stringValue("compose.swing.render.on.graphics"))
  }
  return JewelComposePanel(content).apply {
    ComposeUiInspector(this)
  }
}