package org.jetbrains.plugins.notebooks.visualization.inlay

import java.util.*

interface JupyterBoundsChangeListener: EventListener {
  fun boundsChanged() {}
}