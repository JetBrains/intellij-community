// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentAspect
import com.intellij.openapi.editor.ex.DocumentAspectList
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.util.Key

internal class DocumentSnapshotImpl private constructor(
  private val text: DocumentText,
  private val aspects: DocumentAspectList,
) : DocumentSnapshot {

  constructor(text: DocumentText) : this(
    text = text,
    aspects = DocumentAspectList.empty(),
  )

  override fun text(): DocumentText {
    return text
  }

  override fun withModStamp(newModStamp: Long, incrementModSeq: Boolean): DocumentSnapshot {
    return withMetadataText(text.withModStamp(newModStamp, incrementModSeq))
  }

  override fun withClearedLineFlags(startLine: Int, endLine: Int, exceptLines: IntArray): DocumentSnapshot {
    return withMetadataText(text.withClearedLineFlags(startLine, endLine, exceptLines))
  }

  override fun <A : DocumentAspect> aspect(key: Key<A>): A? {
    @Suppress("UNCHECKED_CAST") // sound because withAspect associates an aspect only with a key of its own type
    return aspects.get(key) as A?
  }

  override fun <A : DocumentAspect> withAspect(key: Key<A>, aspect: A?): DocumentSnapshot {
    val newAspects = if (aspect == null) {
      aspects.remove(key)
    } else {
      aspects.add(key, aspect)
    }
    if (newAspects === aspects) {
      return this
    }
    return DocumentSnapshotImpl(text, newAspects)
  }

  override fun withMetadata(metadata: DocumentSnapshot): DocumentSnapshot {
    if (this === metadata) {
      return this
    }
    val metadataText = metadata.text()
    val newText = text.withMetadata(metadataText)
    if (newText === metadataText) {
      // the texts share the characters, so aspects follow the newest snapshot
      return metadata
    }
    // metadata.text is discarded, so are its aspects,
    // see the reconciliation note in [com.intellij.openapi.editor.ex.DocumentMutator]
    return DocumentSnapshotImpl(newText, aspects)
  }

  override fun withPatch(patch: DocumentTextPatch): DocumentSnapshot {
    val newText = text.withPatch(patch)
    val newAspects = aspects.transform {
      it.withTextChange(text, newText, patch)
    }
    return DocumentSnapshotImpl(newText, newAspects)
  }

  /**
   * Returns a snapshot carrying [newText], or `this` when nothing changed.
   *
   * [newText] must hold the same characters as the current one: the aspects are carried over as they are,
   * a change of the characters has to go through [withPatch], which rebuilds them.
   */
  private fun withMetadataText(newText: DocumentText): DocumentSnapshot {
    if (newText === text) {
      return this
    }
    assert(newText.chars() === text.chars()) {
      "a metadata-only update must keep the characters, use withPatch to change them"
    }
    return DocumentSnapshotImpl(newText, aspects)
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
    val aspectsId = Integer.toHexString(System.identityHashCode(aspects))
    return "DocumentSnapshot@$id{text=$text, aspects=@$aspectsId}"
  }
}
