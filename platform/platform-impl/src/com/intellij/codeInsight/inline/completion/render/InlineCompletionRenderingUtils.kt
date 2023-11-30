// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.editor.Editor

internal fun String.formatBeforeRendering(editor: Editor): String {
  val tabSize = editor.settings.getTabSize(editor.project)
  val tab = " ".repeat(tabSize)
  return replace("\t", tab)
}
