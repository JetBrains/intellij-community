package com.intellij.database.run.ui.table

import org.jetbrains.annotations.ApiStatus
import javax.swing.ListSelectionModel

/**
 * How an extended range changes a selection. By JTable's rule, Shift sets the range; Ctrl+Shift adds it when the anchor cell is selected,
 * and removes it otherwise.
 */
@ApiStatus.Internal
enum class RangeChange {
  REPLACE, ADD, REMOVE;

  /** Applies the range from [anchor] to [lead] to [selection], which then has that anchor and lead, as JTable leaves them. */
  fun applyTo(selection: ListSelectionModel, anchor: Int, lead: Int) {
    when (this) {
      REPLACE -> selection.setSelectionInterval(anchor, lead)
      ADD -> selection.addSelectionInterval(anchor, lead)
      REMOVE -> selection.removeSelectionInterval(anchor, lead)
    }
  }

  companion object {
    /** The change an extend makes by the rule above; [toggle] says whether Ctrl is held. */
    @JvmStatic
    fun forExtend(toggle: Boolean, anchorSelected: Boolean): RangeChange = when {
      !toggle -> REPLACE
      anchorSelected -> ADD
      else -> REMOVE
    }
  }
}
