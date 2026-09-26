// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.text.TextRange
import kotlinx.serialization.Serializable

typealias VCol = Float

@Serializable
data class Caret(val position: CaretPosition,
                 val caretId: CaretId,
                 val vCol: VCol? = null,
                 val visible: Boolean = true) {
  val offset: Long
    get() =
      position.offset

  val selection: TextRange
    get() =
      TextRange(position.selectionStart, position.selectionEnd)

  fun hasSelection(): Boolean =
    position.hasSelection()

  fun move(position: CaretPosition, vCol: VCol? = null): Caret =
    copy(position = position, vCol = vCol)

  fun setVisibility(visible: Boolean): Caret =
    copy(visible = visible)
}
