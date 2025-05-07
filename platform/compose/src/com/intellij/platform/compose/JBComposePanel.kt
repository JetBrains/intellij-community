// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.compose

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.jewel.bridge.JewelComposePanel
import javax.swing.JComponent

@Suppress("FunctionName")
@Experimental
@Deprecated(
  "Use JewelComposePanel instead and make sure you also change the modules to the ones under intellij.platform.jewel.",
            replaceWith = ReplaceWith(
              "JewelComposePanel { content() }",
              "org.jetbrains.jewel.bridge.JewelComposePanel"
            )
)
fun JBComposePanel(
  content: @Composable () -> Unit,
): JComponent {
  return JewelComposePanel { content() }
}