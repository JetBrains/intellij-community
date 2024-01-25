// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import javax.swing.AbstractListModel
import javax.swing.ComboBoxModel

class JavaLoggerModel(loggerList: List<String>,
                      initialLogger: String?) : AbstractListModel<String>(), ComboBoxModel<String> {
  private val loggers = loggerList

  private var currentLogger = initialLogger ?: throw IllegalStateException()

  override fun setSelectedItem(anItem: Any) {
    if (anItem !is String) return
    if (currentLogger != anItem) {
      currentLogger = anItem
      fireContentsChanged(this, -1, -1)
    }
  }

  override fun getSelectedItem(): String {
    return currentLogger
  }

  override fun getSize(): Int {
    return loggers.size
  }

  override fun getElementAt(index: Int): String {
    return loggers[index]
  }
}
