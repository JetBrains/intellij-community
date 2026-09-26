package com.intellij.notebooks.visualization.outputs

/**
 * This interface is used to inform the component that it is really visible to user, for example when scroll changes.
 *
 * Swing components already have methods:
 * Component#isDisplayable - component in displayable hierarchy
 * Component#isVisible - when component has "visible" flag
 * Component#isShowing - isDisplayable+isVisible
 */
interface NotebookOutputInlayShowable {
  /** true when component displayed, false when hidden from user. */
  var shown: Boolean
}