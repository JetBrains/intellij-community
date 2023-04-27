// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.lang.documentation.ide.impl.DocumentationManager.Companion.instance
import javax.swing.event.HyperlinkEvent

internal object DocRenderDefaultLinkActivationHandler : DocRenderLinkActivationHandler {
  override fun activateLink(event: HyperlinkEvent, renderer: DocRenderer) {
    val location = getLocation(event) ?: return
    val url = event.description
    val item = renderer.item
    val editor = item.editor
    val project = editor.project ?: return
    if (isGotoDeclarationEvent) {
      instance(project).navigateInlineLink(
        url) { item.getInlineDocumentationTarget() }
    }
    else {
      instance(project).activateInlineLink(
        url, { item.getInlineDocumentationTarget() },
        editor, popupPosition(location, renderer)
      )
    }
  }
}