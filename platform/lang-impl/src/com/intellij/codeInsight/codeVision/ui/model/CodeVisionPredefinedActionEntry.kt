// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.openapi.editor.Editor

/**
 * Implements by [com.intellij.codeInsight.codeVision.CodeVisionEntry] when click should be processed by CodeVisionEntry itself, not [com.intellij.codeInsight.codeVision.CodeVisionProvider]
 * @see [com.intellij.codeInsight.codeVision.CodeVisionProvider.handleClick]
 * @see [com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider.handleClick]
 */
interface CodeVisionPredefinedActionEntry {
  fun onClick(editor: Editor)
}