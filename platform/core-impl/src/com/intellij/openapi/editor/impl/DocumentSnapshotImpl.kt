// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentSputnik
import com.intellij.openapi.editor.ex.DocumentSputniks
import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.util.Key

internal class DocumentSnapshotImpl private constructor(
  private val text: DocumentText,
  private val modState: DocumentModState,
  private val sputniks: DocumentSputniks,
) : DocumentSnapshot {

  constructor(text: DocumentText) : this(
    text = text,
    modState = DocumentModStateImpl(),
    sputniks = DocumentSputniksImpl.EMPTY,
  )

  override fun text(): DocumentText {
    return text
  }

  override fun modState(): DocumentModState {
    return modState
  }

  override fun withModStamp(newModStamp: Long, incrementModSeq: Boolean): DocumentSnapshot {
    val newModState = modState.withStamp(newModStamp, incrementModSeq)
    if (newModState === modState) {
      return this
    }
    return DocumentSnapshotImpl(text, newModState, sputniks)
  }

  override fun withClearedLineFlags(startLine: Int, endLine: Int, exceptLines: IntArray): DocumentSnapshot {
    val newModState = modState.withClearedLineFlags(text, startLine, endLine, exceptLines)
    assertLineCountsAgree(text, newModState)
    if (newModState === modState) {
      return this
    }
    return DocumentSnapshotImpl(text, newModState, sputniks)
  }

  override fun <S : DocumentSputnik> sputnik(key: Key<S>): S? {
    @Suppress("UNCHECKED_CAST") // sound because withSputnik associates a sputnik only with a key of its own type
    return sputniks.get(key) as S?
  }

  override fun <S : DocumentSputnik> withSputnik(key: Key<S>, sputnik: S?): DocumentSnapshot {
    val newSputniks = if (sputnik == null) {
      sputniks.remove(key)
    } else {
      sputniks.add(key, sputnik)
    }
    if (newSputniks === sputniks) {
      return this
    }
    return DocumentSnapshotImpl(text, modState, newSputniks)
  }

  override fun withMetadata(metadata: DocumentSnapshot): DocumentSnapshot {
    if (this === metadata) {
      return this
    }
    val metadataText = metadata.text()
    if (text.chars() === metadataText.chars()) {
      return metadata
    }
    val newModState = modState.withMetadata(metadata.modState())
    assertLineCountsAgree(text, newModState)
    if (newModState === modState) {
      return this
    }
    return DocumentSnapshotImpl(text, newModState, sputniks)
  }

  override fun withPatch(patch: DocumentTextPatch): DocumentSnapshot {
    val newText = text.withPatch(patch)
    if (newText === text) {
      return this
    }
    val newModState = modState.withPatch(text, patch)
    assertLineCountsAgree(newText, newModState)
    val oldSnapshot = this
    val newSnapshot = DocumentSnapshotImpl(newText, newModState, sputniks)
    if (sputniks === DocumentSputniksImpl.EMPTY) {
      return newSnapshot
    }
    return sputniks.withTextChange(oldSnapshot, newSnapshot, patch) { newSputniks ->
      DocumentSnapshotImpl(newText, newModState, newSputniks)
    }
  }

  /**
   * Guards against [DocumentModState]'s independently-maintained line structure (see [ModifiedLineSet])
   * silently drifting from [text]'s -- both must agree on line count whenever [modState]'s tracking has
   * actually been built (`null` means it hasn't, and there is nothing yet to compare).
   */
  private fun assertLineCountsAgree(text: DocumentText, modState: DocumentModState) {
    val modStateLineCount = modState.lineCount()
    assert(modStateLineCount == null || modStateLineCount == text.lineCount()) {
      "text.lineCount() = " + text.lineCount() + "; modState.lineCount() = " + modStateLineCount
    }
  }

  override fun dumpState(): String {
    val dump = StringBuilder()
    dump.append("intervals:\n")
    val lineCount: Int = text.lineCount()
    for (line in 0..<lineCount) {
      dump
        .append(line)
        .append(": ")
        .append(text.lineStartOffset(line))
        .append("-")
        .append(text.lineEndOffset(line))
        .append(", ")
    }
    if (lineCount > 0) {
      dump.setLength(dump.length - 2)
    }
    return dump.toString()
  }

  override fun toString(): String {
    val id = Integer.toHexString(System.identityHashCode(this))
    val sputnikId = Integer.toHexString(System.identityHashCode(sputniks))
    return "DocumentSnapshot@$id{text=$text, modState=$modState, sputniks=@$sputnikId}"
  }
}
