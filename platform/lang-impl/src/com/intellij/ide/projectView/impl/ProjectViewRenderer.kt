// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.navigation.ItemPresentation
import com.intellij.ui.SimpleTextAttributes

open class ProjectViewRenderer : NodeRenderer() {

  init {
    isOpaque = false
    isIconOpaque = false
    isTransparentIconBackground = true
  }

  override fun getPresentation(node: Any?): ItemPresentation? {
    val originalPresentation = super.getPresentation(node)
    if (originalPresentation !is PresentationData || isGrayedTextPaintingEnabled || originalPresentation.coloredText.isEmpty()) {
      return originalPresentation
    }
    val presentation = originalPresentation.clone()
    presentation.clearText()
    for (fragment in originalPresentation.coloredText) {
      if (fragment.attributes.fgColor == SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor) {
        break
      }
      presentation.addText(fragment)
    }
    return presentation
  }
}

// A dirty hack to paint project view tree nodes without inplace comments
// and other grayed out parts. A global flag used only in the EDT during the painting.
internal var isGrayedTextPaintingEnabled = true
