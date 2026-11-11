// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.compose.swing.components.style.TitledSeparatorKind
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import com.intellij.ui.TitledSeparator as IdeaTitledSeparator
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.styleable

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
  style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
) {
  SwingNode(
    factory = { IdeaTitledSeparator() },
    modifier = modifier.styleable(TitledSeparatorKind, style),
    update = {
      set(text) { this.text = it }
    },
  )
}
