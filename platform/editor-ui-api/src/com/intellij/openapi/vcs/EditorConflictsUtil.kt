// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.lang.EditorConflictSupport.*
import com.intellij.lang.EditorConflictSupport.ConflictMarkerType.*
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.DocumentUtil
import kotlin.coroutines.experimental.buildSequence


fun getConflictMarkerType(marker: PsiElement?) = when {
  marker?.node?.elementType != TokenType.CONFLICT_MARKER -> null
  else -> getConflictMarkerType(marker?.text)
}


fun getNextMarker(marker: PsiElement): PsiElement? {
  assert(marker.node?.elementType == TokenType.CONFLICT_MARKER)
  val type = getConflictMarkerType(marker) ?: return null
  val next = leafsSeq(marker, true, false).firstOrNull { it.node?.elementType == TokenType.CONFLICT_MARKER }
  val nextType = getConflictMarkerType(next) ?: return null
  return when {
    next != null && isNextMarker(type, nextType) -> next
    else -> null
  }
}

fun leafsSeq(e: PsiElement, fwd: Boolean, includeFirst: Boolean) = buildSequence {
  var cur = e
  var first = true
  while (true) {
    if (includeFirst || !first) yield(cur)
    first = false
    val next = if (fwd) PsiTreeUtil.nextLeaf(cur) else PsiTreeUtil.prevLeaf(cur)
    if (next == null) return@buildSequence
    cur = next
  }
}


fun getSectionInnerRange(begin: PsiElement, end: PsiElement, d: Document) =
  TextRange(begin.rangeWithLine(d).endOffset + 1, end.rangeWithLine(d).startOffset)

fun getPrevMarker(marker: PsiElement): PsiElement? {
  assert(marker.node?.elementType == TokenType.CONFLICT_MARKER)
  val type = getConflictMarkerType(marker) ?: return null
  val next = leafsSeq(marker, false, false).firstOrNull { it.node?.elementType == TokenType.CONFLICT_MARKER }
  val nextType = getConflictMarkerType(next) ?: return null
  return when {
    next != null && isPrevMarker(type, nextType) -> next
    else -> null
  }
}

fun getFirstMarkerFromGroup(marker: PsiElement): PsiElement {
  var cur = marker
  while (true) {
    val prev = getPrevMarker(cur)
    if (prev == null)
      return cur
    else
      cur = prev
  }
}

fun getLastMarkerFromGroup(marker: PsiElement): PsiElement {
  var cur = marker
  while (true) {
    val next = getNextMarker(cur)
    if (next == null)
      return cur
    else
      cur = next
  }
}

data class MarkerGroup(val first: PsiElement, val last: PsiElement) {
  fun getMarker(type: ConflictMarkerType): PsiElement? {
    return leafsSeq(first, true, true)
      .takeWhile {
        getConflictMarkerType(it) != AfterLast
      }
      .firstOrNull {
        getConflictMarkerType(it) == type
      }
  }
}

fun getMarkerGroup(marker: PsiElement) = MarkerGroup(getFirstMarkerFromGroup(marker), getLastMarkerFromGroup(marker))

fun isNextMarker(type: ConflictMarkerType, nextType: ConflictMarkerType?) = when (type) {
  BeforeFirst -> nextType == BeforeMerged || nextType == BeforeLast
  BeforeMerged -> nextType == BeforeLast
  BeforeLast -> nextType == AfterLast
  AfterLast -> false
}

fun isPrevMarker(type: ConflictMarkerType, nextType: ConflictMarkerType?) = when (type) {
  BeforeFirst -> false
  BeforeMerged -> nextType == BeforeFirst
  BeforeLast -> nextType == BeforeMerged || nextType == BeforeFirst
  AfterLast -> nextType == BeforeLast
}

fun PsiElement.rangeWithLine(d: Document) =
  TextRange(DocumentUtil.getLineStartOffset(textRange.startOffset, d), DocumentUtil.getLineEndOffset(textRange.endOffset, d))

fun Document.substring(textRange: TextRange) =
  immutableCharSequence.substring(textRange.startOffset, textRange.endOffset)
