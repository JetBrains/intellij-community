package org.jetbrains.plugins.notebooks.visualization

interface MarkdownInlayRenderingController {  //  PY-73017.
  // This is a temporary interface to trigger the stopRendering() function from this package.
  // todo: a refactor will be required as part of the ongoing reorganization of Jupyter modules
  fun stopRendering()
}
