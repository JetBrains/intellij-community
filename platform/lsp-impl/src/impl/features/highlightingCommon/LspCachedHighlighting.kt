package com.intellij.platform.lsp.impl.features.highlightingCommon

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.Contract

/**
 * Stores some [highlighting-related information][highlightingInfo] received from an LSP server,
 * and also a [text range][textRange] to which this information applies.
 */
internal data class LspCachedHighlighting<T>(

  /**
   * A text range that has some kind of highlighting-related data associated with it.
   */
  val textRange: TextRange,

  /**
   * Any data that describes some kind of highlighting related to the [textRange].
   * For example, syntax highlighting, diagnostics, inlay hint-related information, etc.
   */
  val highlightingInfo: T,
)


internal class PendingEdit(
  val offset: Int,
  val oldLength: Int,
  val newLength: Int,
)


/**
 * Adjusts highlighting ranges after document edits.
 *
 * Edits fully contained in a highlighting grow/shrink it; edits that only partially overlap a highlighting
 * remove it; edits entirely outside a highlighting shift it as needed. The next authoritative update from
 * the server is expected to replace the adjusted highlightings soon after.
 */
@Contract(pure = true)
internal fun <T> applyPendingEdits(
  highlightings: List<LspCachedHighlighting<T>>,
  edits: Collection<PendingEdit>,
): List<LspCachedHighlighting<T>> {
  if (edits.isEmpty()) return highlightings

  val updatedHighlightings = highlightings.toMutableList()
  for (edit in edits) {
    ProgressManager.checkCanceled()
    applyPendingEdit(updatedHighlightings, edit)
  }
  return updatedHighlightings
}

private fun <T> applyPendingEdit(
  highlightings: MutableList<LspCachedHighlighting<T>>,
  edit: PendingEdit,
) {
  val iterator = highlightings.listIterator()
  while (iterator.hasNext()) {
    val highlighting = iterator.next()
    val textRange = highlighting.textRange
    val editOldEndOffset = edit.offset + edit.oldLength

    if (edit.offset >= textRange.endOffset) {
      // edit range is after this highlighting.textRange ⇒ no need to update the range
      continue
    }

    if (editOldEndOffset <= textRange.startOffset) {
      // edit range is before this highlighting.textRange ⇒ need to shift the range
      val newTextRange = textRange.shiftRight(edit.newLength - edit.oldLength)
      iterator.set(LspCachedHighlighting(newTextRange, highlighting.highlightingInfo))
      continue
    }

    if (edit.offset >= textRange.startOffset && editOldEndOffset <= textRange.endOffset) {
      // changes within the highlighting.textRange ⇒ update the range
      val newTextRange = textRange.grown(edit.newLength - edit.oldLength)
      iterator.set(LspCachedHighlighting(newTextRange, highlighting.highlightingInfo))
      continue
    }

    // changed range intersects with the highlighting.textRange ⇒ delete highlighting
    iterator.remove()
  }
}
