// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.editor.Editor
import kotlin.math.max

object HintUtil {
  fun getSize(editor: Editor): Float = max(1f, editor.colorsScheme.editorFontSize2D - 1f)
}