package com.intellij.notebooks.visualization.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/** General UI settings for all notebooks, R/Kotlin/Jupyter. */
@State(name = "NotebookSettings", storages = [(Storage(value = "notebook-settings.xml"))], category = SettingsCategory.UI)
class NotebookSettings: PersistentStateComponent<NotebookSettings>, Cloneable {

  /** When <=0, max height of output is limited by 30% of screen height. When set, it is calculated in the height of single text line. */
  var outputMaxHeightInEditorLines: Int = -1

  override fun getState(): NotebookSettings = this

  override fun loadState(state: NotebookSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(): NotebookSettings = service()
  }
}