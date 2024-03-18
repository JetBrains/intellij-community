// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider.EP_NAME
import com.intellij.psi.PsiFile
import com.intellij.ui.ColorUtil
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls

@RequiresReadLock
@RequiresBackgroundThread
internal fun inlineDocumentationItems(file: PsiFile): List<InlineDocumentation> {
  val result = SmartList<InlineDocumentation>()
  for (provider in EP_NAME.extensionList) {
    result.addAll(provider.inlineDocumentationItems(file))
  }
  return result
}

@RequiresReadLock
@RequiresBackgroundThread
internal fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? {
  return EP_NAME.extensionList.firstNotNullOfOrNull {
    it.findInlineDocumentation(file, textRange)
  }
}

@NlsSafe
const val START_TIP_PREFIX = "<tip>"

@NlsSafe
const val END_TIP_SUFFIX = "</tip>"

internal fun unwrapTipsText(text: @Nls String): @Nls String {
  if (!text.startsWith(START_TIP_PREFIX) || !text.endsWith(END_TIP_SUFFIX)) error("Invalid text: $text")
  return text.substring(START_TIP_PREFIX.length, text.length - END_TIP_SUFFIX.length)
}

internal fun createAdditionalStylesForTips(editor: Editor): String {
  val defaultBackground = ColorUtil.toHtmlColor(editor.colorsScheme.getDefaultBackground())
  val foreground = ColorUtil.toHtmlColor(JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND)
  val border = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Button.disabledOutlineColor())

  // BOLD redefine is not working well: "b {font-weight: normal; color: #000000} " +
  return ".shortcut {font-weight: bold; color: $foreground; background-color: $defaultBackground; border-color: $border}"
}
