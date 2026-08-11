// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText

internal class DocumentSnapshotImpl(
  private val text: DocumentText,
) : DocumentSnapshot {

  override fun text(): DocumentText {
    return text
  }

  override fun withText(text: DocumentText): DocumentSnapshot {
    return if (text === this.text) {
      this
    } else {
      DocumentSnapshotImpl(text)
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
    return "DocumentSnapshot@$id{text=$text}"
  }
}
