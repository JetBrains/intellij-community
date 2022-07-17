package org.jetbrains.plugins.notebooks.visualization

/**
 * After all inlays updated [inlaysChanged] is called by [NotebookCellInlayManager]
 */
interface InlaysChangedListener {
  fun inlaysChanged()
}
