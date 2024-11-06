package com.intellij.notebooks.visualization.inlay

import java.util.*

interface JupyterBoundsChangeListener : EventListener {
  fun boundsChanged() = Unit
}