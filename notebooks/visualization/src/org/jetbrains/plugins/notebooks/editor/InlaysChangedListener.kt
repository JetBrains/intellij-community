package org.jetbrains.plugins.notebooks.editor

/**
 * After all inlays updated [inlaysChanged] is called by [NotebookCellInlayManager]
 */
interface InlaysChangedListener {
  fun inlaysChanged()
}
