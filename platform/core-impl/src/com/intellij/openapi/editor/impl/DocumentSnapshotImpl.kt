// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentSputnik
import com.intellij.openapi.editor.ex.DocumentSputniks
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.PMarkerRootImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.util.Key
import java.util.concurrent.atomic.AtomicReference

internal class DocumentSnapshotImpl private constructor(
  private val text: DocumentText,
  private val modState: DocumentModState,
  private val sputniks: DocumentSputniks,
) : DocumentSnapshot {

  internal val markerRoot: AtomicReference<PMarkerRoot> = AtomicReference(PMarkerRootImpl.empty())

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

  override fun <S : DocumentSputnik> sputnik(key: Key<S>): S? {
    @Suppress("UNCHECKED_CAST") // sound because setSputnik associates a sputnik only with a key of its own type
    return sputniks.get(key) as S?
  }

  override fun withMetadata(metadata: DocumentSnapshot): DocumentSnapshot {
    if (this === metadata || text === metadata.text()) {
      return metadata
    }
    return this
  }

  override fun applyOp(op: DocumentOp): DocumentSnapshot {
    val newText = text.applyOp(op)
    val newModState = modState.applyOp(text, newText, op)
    val canAffectSputniks = op is DocumentOp.SetSputnik || newText !== text
    if (newText === text && newModState === modState && !canAffectSputniks) {
      return this
    }
    val newSnapshot = if (newText === text && newModState === modState) {
      this
    }
    else {
      DocumentSnapshotImpl(newText, newModState, sputniks)
    }
    val after = if (canAffectSputniks && (sputniks !== DocumentSputniksImpl.EMPTY || op is DocumentOp.SetSputnik)) {
      sputniks.applyOp(this, newSnapshot, op) { newSputniks ->
        DocumentSnapshotImpl(newText, newModState, newSputniks)
      }
    }
    else {
      newSnapshot
    }
    if (after !== this) {
      SnapshotMarkerEngineImpl.applyOp(this, after, op)
    }
    return after
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
