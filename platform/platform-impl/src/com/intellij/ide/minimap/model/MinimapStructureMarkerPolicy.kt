// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName

interface MinimapStructureMarkerPolicy {
  fun isApplicable(editor: Editor): Boolean = true

  fun isRelevantStructureElement(element: StructureViewTreeElement, value: Any): Boolean = true

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MinimapStructureMarkerPolicy> = ExtensionPointName("com.intellij.minimapStructureMarkerPolicy")

    fun forEditor(editor: Editor): MinimapStructureMarkerPolicy {
      return EP_NAME.extensionList.firstOrNull { it.isApplicable(editor) } ?: DefaultMinimapStructureMarkerPolicy
    }
  }
}