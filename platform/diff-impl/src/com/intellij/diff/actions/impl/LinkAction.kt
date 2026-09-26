// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.ide.setToolTipText
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.AnActionLink
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
@ApiStatus.Experimental
abstract class LinkAction : AnAction(), CustomComponentAction {
  final override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return AnActionLink(this, place)
  }

  final override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val link = component as? AnActionLink ?: return

    link.apply {
      isEnabled = presentation.isEnabled
      isVisible = presentation.isVisible
      text = presentation.text
      setToolTipText(presentation.description?.let { HtmlChunk.raw(it) })
    }
  }
}
