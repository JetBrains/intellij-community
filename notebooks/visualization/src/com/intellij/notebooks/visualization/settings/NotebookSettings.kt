package com.intellij.notebooks.visualization.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/** General UI settings for all notebooks, R/Kotlin/Jupyter. */
@State(name = NotebookSettings.COMPONENT_NAME, storages = [(Storage(value = "notebook-settings.xml"))], category = SettingsCategory.UI)
class NotebookSettings : PersistentStateComponent<NotebookSettings>, Cloneable {

  /** When <=0, the max height of output is limited by 30% of screen height. When set, it is calculated in the height of a single text line. */
  var outputMaxHeightInEditorLines: Int = -1

  /**
   * Top-right-cell-corner-toolbar visibility for the selected cell.
   * Toolbar will be in any case visible for the hovered cell.
   */
  var showToolbarForSelectedCell: Boolean = false

  /** The top-right-cell-corner-toolbar will always be visible for the large cells and will stick to the editor top. */
  var cellToolbarStickyVisible: Boolean = true

  override fun getState(): NotebookSettings = this

  override fun loadState(state: NotebookSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun noStateLoaded() {
    loadState(NotebookSettings())
  }

  companion object {
    const val COMPONENT_NAME: String = "NotebookSettings"
    fun getInstance(): NotebookSettings = service()
  }
}