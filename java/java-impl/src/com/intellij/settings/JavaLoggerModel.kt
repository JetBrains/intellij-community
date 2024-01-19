// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import javax.swing.AbstractListModel
import javax.swing.ComboBoxModel

class JavaLoggerModel(loggersList : List<JavaLoggerInfo>, initialLogger : JavaLoggerInfo) : AbstractListModel<JavaLoggerInfo>(), ComboBoxModel<JavaLoggerInfo> {
  private val loggers = loggersList

  private var currentLogger = initialLogger

  override fun setSelectedItem(anItem: Any) {
    if (anItem !is JavaLoggerInfo) return
    if (currentLogger != anItem) {
      currentLogger = anItem
      fireContentsChanged(this, -1, -1)
    }
  }

  override fun getSelectedItem(): Any {
    return currentLogger
  }

  override fun getSize(): Int {
    return loggers.size
  }

  override fun getElementAt(index: Int): JavaLoggerInfo {
    return loggers[index]
  }
}
