package com.intellij.notebooks.ui

/**
 * We have global IdeEventQueue.getInstance().addDispatcher listener [SelectClickedCellEventDispatcher], which process every mouse
 * click for the whole app and selects cell under mouse.
 *
 *  We can use SKIP_CLICK_PROCESSING_FOR_CELL_SELECTION flag to stop click processing for our over-the-cell component,
 *  to prevent cell clicking.
 */
object SelectClickedCellEventHelper {
  const val SKIP_CLICK_PROCESSING_FOR_CELL_SELECTION: String = "SkipClickProcessingForCellSelection"
}