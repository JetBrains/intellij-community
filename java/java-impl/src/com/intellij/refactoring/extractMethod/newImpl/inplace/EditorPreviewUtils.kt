// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.openapi.editor.Editor
import java.awt.*

object EditorPreviewUtils {

  fun addPreviewRanges(editor: Editor, lineRanges: List<IntRange>): EditorCodePreview {
    val preview = EditorCodePreview(editor, lineRanges)
    return preview
  }

}

private operator fun IntRange.contains(range: IntRange): Boolean {
  return this.first <= range.first && this.last >= range.last
}

val IntRange.length: Int
  get() = last - first + 1