// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.lang.documentation.ide.DocumentationCustomization
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry

internal object DefaultDocumentationCustomization : DocumentationCustomization {
  override val isAutoShowOnLookupItemChange: Boolean
    get() = CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO
  override val autoShowDelayMillis: Long
    get() = CodeInsightSettings.getInstance().JAVADOC_INFO_DELAY.toLong()
  override val isShowToolbar: Boolean
    get() = Registry.`is`("documentation.show.toolbar", false)

  override fun isAvailable(editor: Editor): Boolean = true
}