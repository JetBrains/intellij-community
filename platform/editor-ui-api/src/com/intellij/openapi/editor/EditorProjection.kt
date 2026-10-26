// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.EditorProjection.Provider
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

/**
 * A projection of a host editor onto one of the documents it shows.
 *
 * The host editor shows the text of several documents interleaved, as the unified diff viewer does. Nothing parses such a text,
 * so code insight runs on a projection instead: an editor over one of those documents, whose caret, selection, scrolling and
 * geometry are the host's, mapped into the offsets of that document. The host registers a [Provider] with [registerProvider],
 * and [resolveAt] gives the projection at a host offset. A projection paints nothing; the host editor does.
 *
 * The use case is similar to the [com.intellij.injected.editor.EditorWindow].
 * An injected window creates and maintains a Document with a copy of a part of the host text.
 * A projection takes an existing Document and its PSI and operates using known offset-to-offset mappings, so a reference from
 * another file, a usage search, a breakpoint and the navigation history reach it, as they reach a plain editor of that file.
 *
 * The [com.intellij.injected.editor.EditorWindow] and [EditorProjection] do not combine.
 * A projection is not an injected editor, and its host is not an injected editor.
 * An injected language fragment inside a projected editor gets no injected editor, because the injection code works only with
 * the real editor implementation.
 */
@ApiStatus.Experimental
interface EditorProjection : Editor {
  /** The editor that shows the document of this editor. */
  val hostEditor: Editor

  /** The host offset that shows [offset] of this document, or -1 when the host does not show it. */
  fun toHostOffset(offset: Int): Int

  /** The offset of this document that the host shows at [hostOffset], or -1 when the host shows another document there. */
  fun fromHostOffset(hostOffset: Int): Int

  /**
   * The host range that shows [range] of this document, or null when the host does not show it as one range.
   * A range can be empty.
   */
  fun toHostRange(range: TextRange): TextRange?

  /** The projections of one host editor. */
  fun interface Provider {
    /** The projection over the document that the host shows at [hostOffset], or null when the host shows its own document there. */
    fun getProjection(hostOffset: Int): EditorProjection?
  }

  companion object {
    @JvmField
    val EDITOR_PROJECTION: DataKey<EditorProjection> = DataKey.create("editor.projection")

    @JvmStatic
    fun registerProvider(editor: Editor, provider: Provider) {
      assert(editor !is EditorWindow)
      editor.putUserData(PROJECTION_PROVIDER_KEY, provider)
    }

    /**
     * @return The [EditorProjection] at the given text offset.
     */
    @JvmStatic
    fun findAt(editor: Editor, offset: Int): EditorProjection? = editor.getUserData(PROJECTION_PROVIDER_KEY)?.getProjection(offset)

    /**
     * @return The [EditorProjection] at the given text offset, or [editor] itself when there is none.
     */
    @JvmStatic
    fun resolveAt(editor: Editor, offset: Int): Editor = findAt(editor, offset) ?: editor

    /**
     * @return The [EditorProjection] at the caret, or [editor] itself when there is none.
     */
    @JvmStatic
    fun resolveAtCaret(editor: Editor): Editor = resolveAt(editor, editor.caretModel.offset)
  }
}

private val PROJECTION_PROVIDER_KEY: Key<Provider> = Key.create("EditorProjection.Provider")
