// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import com.intellij.settings.Logger.Companion.allLoggers
import javax.swing.AbstractListModel
import javax.swing.ComboBoxModel

class JavaLoggerModel(initialLogger : Logger) : AbstractListModel<Logger>(), ComboBoxModel<Logger> {
  private val loggers = allLoggers

  private var currentLogger = initialLogger

  override fun setSelectedItem(anItem: Any) {
    if (anItem !is Logger) return
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

  override fun getElementAt(index: Int): Logger {
    return loggers[index]
  }
}
