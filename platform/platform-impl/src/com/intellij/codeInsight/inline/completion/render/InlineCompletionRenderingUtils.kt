// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

internal fun String.formatTabs(tabSize: Int): String {
  val tab = " ".repeat(tabSize)
  return replace("\t", tab)
}
