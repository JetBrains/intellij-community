package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

object JupyterToolbarVisibilityManager {
  // simple singleton to keep only one active intercellular toolbar
  // not sure whether a more complex observer solution is actually needed
  private var currentActiveManager: JupyterToolbarManager? = null

  fun requestToolbarDisplay(manager: JupyterToolbarManager) {
    if (currentActiveManager != manager) {
      currentActiveManager?.hideToolBar()
      currentActiveManager = manager
    }
  }

  fun notifyToolbarHidden(manager: JupyterToolbarManager) {
    if (manager == currentActiveManager) {
      currentActiveManager = null
    }
  }
}
