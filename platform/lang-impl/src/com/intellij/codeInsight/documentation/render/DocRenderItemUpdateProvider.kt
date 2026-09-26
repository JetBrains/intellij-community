// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DocRenderItemUpdateProvider {
  fun getItems(editor: Editor): Collection<DocRenderItem>

  companion object {
    private val EP_NAME: ExtensionPointName<DocRenderItemUpdateProvider> =
      ExtensionPointName.create("com.intellij.codeInsight.documentation.render.itemUpdateProvider")

    @JvmStatic
    fun getAllItems(editor: Editor): List<DocRenderItem> {
      return EP_NAME.extensionList.flatMap { it.getItems(editor) }
    }
  }
}
