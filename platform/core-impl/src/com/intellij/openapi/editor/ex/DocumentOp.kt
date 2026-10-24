// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.impl.CleanLinesOpImpl
import com.intellij.openapi.editor.impl.DeleteOpImpl
import com.intellij.openapi.editor.impl.InsertOpImpl
import com.intellij.openapi.editor.impl.ModStampOpImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

@ApiStatus.Internal
sealed interface DocumentOp {

  /**
   * An op that changes the text: [DocumentOp.Insert] or [DocumentOp.Delete].
   */
  sealed interface Text : DocumentOp {
    /**
     * The position in the document that this op changes.
     */
    fun offset(): Int

    /**
     * The number of characters that this op inserts or deletes.
     */
    fun length(): Int

    /**
     * The offset of the other half when this op is half of a text move, as
     * [com.intellij.openapi.editor.event.DocumentEvent.getMoveOffset] defines it. An insert names its
     * source in the text after the insert. A delete names its copy in the text before the delete. An op
     * that moves nothing returns [offset].
     */
    fun moveOffset(): Int
  }

  interface Insert : Text {
    /**
     * The fragment of text that this op inserts.
     */
    fun fragment(): CharSequence
  }

  /**
   * A delete carries no content, so [offset] and [length] describe it in full.
   */
  interface Delete : Text

  interface ModStamp : DocumentOp {
    fun modStamp(): Long
    fun incSequence(): Boolean
  }

  interface CleanLines : DocumentOp {
    fun startLine(): Int
    fun endLine(): Int
    fun exceptLines(): IntArray
  }

  companion object {
    /**
     * An insert of [fragment] at [offset]. The op detaches itself from [fragment], so a
     * caller may keep changing a mutable sequence afterwards. The copy is free when the
     * sequence is already immutable, which a `String` is.
     *
     * The op does not check the bounds. The document rejects an offset it cannot use, and an empty
     * fragment is a legal op that changes nothing.
     */
    @JvmStatic
    fun insertOp(offset: Int, fragment: CharSequence): Insert {
      return InsertOpImpl(offset, fragment, offset)
    }

    /**
     * An insert of [fragment] at [offset] that is the first half of a text move from [moveOffset]. See
     * [Text.moveOffset]. The op checks neither the bounds nor the move offset.
     */
    @JvmStatic
    fun insertOp(offset: Int, fragment: CharSequence, moveOffset: Int): Insert {
      return InsertOpImpl(offset, fragment, moveOffset)
    }

    /**
     * A delete of [length] characters at [offset]. A zero length is a legal op that changes nothing.
     */
    @JvmStatic
    fun deleteOp(offset: Int, length: Int): Delete {
      return DeleteOpImpl(offset, length, offset)
    }

    /**
     * A delete of [length] characters at [offset] that is the second half of a text move to [moveOffset].
     * See [Text.moveOffset]. The op checks neither the bounds nor the move offset.
     */
    @JvmStatic
    fun deleteOp(offset: Int, length: Int, moveOffset: Int): Delete {
      return DeleteOpImpl(offset, length, moveOffset)
    }

    @JvmStatic
    fun modStampOp(modStamp: Long, incSequence: Boolean): ModStamp {
      return ModStampOpImpl(modStamp, incSequence)
    }

    @JvmStatic
    fun cleanLinesOp(startLine: Int, endLine: Int, exceptLines: IntArray): CleanLines {
      return CleanLinesOpImpl(startLine, endLine, exceptLines)
    }
  }
}

/**
 * Returns the text with [op] applied.
 * The move offset of [op] does not change the text.
 */
@ApiStatus.Internal
@Contract(pure = true)
fun DocumentText.applyOp(op: DocumentOp.Text): DocumentText {
  return when (op) {
    is DocumentOp.Insert -> insert(op.offset(), op.fragment())
    is DocumentOp.Delete -> delete(op.offset(), op.offset() + op.length())
  }
}
