// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import com.intellij.ui.components.BrowserLink as IdeaBrowserLink

/**
 * @see com.intellij.ui.components.BrowserLink
 * @see com.intellij.ui.dsl.builder.Row.browserLink
 */
@Composable
@ApiStatus.Experimental
public fun BrowserLink(
  url: @NonNls String,
  modifier: SwingModifier = SwingModifier,
) {
  SwingNode(
    factory = { IdeaBrowserLink(url) },
    update = {
      set(url) {
        text = it
        this.url = it
      }
      applyModifier(modifier)
    },
  )
}

/**
 * @see com.intellij.ui.components.BrowserLink
 * @see com.intellij.ui.dsl.builder.Row.browserLink
 */
@Composable
@ApiStatus.Experimental
public fun BrowserLink(
  text: @NlsContexts.LinkLabel String,
  url: @NonNls String,
  modifier: SwingModifier = SwingModifier,
) {
  SwingNode(
    factory = { IdeaBrowserLink(text, url) },
    update = {
      set(text) { this.text = it }
      update(url) { this.url = it }
      applyModifier(modifier)
    },
  )
}
