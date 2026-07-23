// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import com.intellij.ui.TitledSeparator as IdeaTitledSeparator

/**
 * A separator carrying a group [text] title, with the space above and below it that a titled separator
 * standing on its own draws.
 *
 * A titled group inside a form is [FormScope.FormGroup], which
 * lets the form decide that space instead.
 *
 * @see com.intellij.ui.TitledSeparator
 * @see com.intellij.ui.dsl.builder.Panel.group
 */
@Composable
@ApiStatus.Experimental
public fun TitledSeparator(
  text: @NlsContexts.Separator String,
  modifier: SwingModifier = SwingModifier,
) {
  SwingNode(
    factory = { IdeaTitledSeparator() },
    update = {
      set(text) { this.text = it }
      applyModifier(modifier)
    },
  )
}
