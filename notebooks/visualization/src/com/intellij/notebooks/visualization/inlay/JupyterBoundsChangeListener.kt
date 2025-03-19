package com.intellij.notebooks.visualization.inlay

import java.util.EventListener

fun interface JupyterBoundsChangeListener : EventListener {
  fun boundsChanged(): Unit?
}