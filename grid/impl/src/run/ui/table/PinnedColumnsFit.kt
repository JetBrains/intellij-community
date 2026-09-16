package com.intellij.database.run.ui.table

import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus
import kotlin.math.roundToInt

/**
 * How much of the grid width the pinned strip may take without needing its recovery scrollbar. A strip that fills the
 * result area puts the divider and everything behind it out of reach (DBE-26267).
 *
 * The divider is painted on the strip's own trailing pixel, so it is already part of the pinned width.
 */
@ApiStatus.Internal
object PinnedColumnsFit {
  private const val SCROLLABLE_FRACTION = 0.2
  private const val MIN_SCROLLABLE_WIDTH = 80
  private const val MAX_SCROLLABLE_WIDTH = 200

  /** Width the unpinned table must keep. Swing reports UI-scaled widths, hence the scaled bounds. */
  @JvmStatic
  fun minimumScrollableWidth(availableWidth: Int): Int =
    (availableWidth * SCROLLABLE_FRACTION).roundToInt()
      .coerceIn(JBUIScale.scale(MIN_SCROLLABLE_WIDTH), JBUIScale.scale(MAX_SCROLLABLE_WIDTH))

  @JvmStatic
  fun maximumPinnedWidth(availableWidth: Int): Int = availableWidth - minimumScrollableWidth(availableWidth)

  /** Largest pinned width that either leaves the normal reserve or lets all unpinned columns fit without scrolling. */
  @JvmStatic
  fun maximumFittingPinnedWidth(availableWidth: Int, unpinnedWidth: Int): Int =
    if (availableWidth <= 0) Int.MAX_VALUE
    else maxOf(0, maximumPinnedWidth(availableWidth), availableWidth - unpinnedWidth)

  /**
   * Whether a strip of [pinnedWidth] leaves the [unpinnedWidth] beside it usable. A grid that is not laid out yet has
   * no width to judge, and one whose columns all fit on screen has nothing to scroll to, so neither ever refuses.
   */
  @JvmStatic
  fun fits(pinnedWidth: Int, unpinnedWidth: Int, availableWidth: Int): Boolean =
    pinnedWidth <= maximumFittingPinnedWidth(availableWidth, unpinnedWidth)
}
