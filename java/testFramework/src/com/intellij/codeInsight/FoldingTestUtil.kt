// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("FoldingTestUtil")

package com.intellij.codeInsight

import com.intellij.openapi.editor.Editor

fun Editor.assertFolded(foldedText: String, placeholder: String) {
  val foldingRegions = foldingModel.allFoldRegions
  val matchingRegion = foldingRegions.firstOrNull {
    it.placeholderText == placeholder && it.document.text.substring(it.startOffset, it.endOffset) == foldedText
  }
  if (matchingRegion == null) {
    val existingFoldingsString = foldingRegions.joinToString {
      "'${it.document.text.substring(it.startOffset, it.endOffset)}' -> '${it.placeholderText}'"
    }
    throw AssertionError("no folding '$foldedText' -> '$placeholder' found in $existingFoldingsString")
  }

}
