// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.data

import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

interface ImportSettingButtonDataProvider {
  companion object {
    internal fun createState(importService: BaseService, list: List<Product>): SettingButtonState? {
      if (list.isNotEmpty()) {
        if (list.size == 1) {
          list[0].let {
            return SettingButtonState(importService.getProductIcon(it.id), it.name, false, true)
          }
        }
        else {
          return SettingButtonState(ImportJbIcon(list) { importService.getProductIcon(it) }, "JetBrains IDes", true, true)
        }
      }

      return null
    }

  }

  fun getButtonState(): SettingButtonState?

  fun updateState()

  fun pressed()
}

data class SettingButtonState(val icon: Icon,
                              val name: String,
                              val isItPopup: Boolean,
                              val enabled: Boolean)

class SyncSettingButtonProvider : ImportSettingButtonDataProvider {
  private val importService = SettingsService.getInstance().getSyncService()
  private var currentState: SettingButtonState? = null
  override fun getButtonState(): SettingButtonState? {
    TODO("Not yet implemented")
  }

  override fun updateState() {
    TODO("Not yet implemented")
  }

  override fun pressed() {
    TODO("Not yet implemented")
  }

}

class ImportExternalButtonDataProvider : ImportSettingButtonDataProvider {
  private val importService = SettingsService.getInstance().getExternalService()
  private var currentState: SettingButtonState? = null

  override fun getButtonState(): SettingButtonState? {
    if(currentState == null) {
      updateState()
    }
    return currentState
  }

  override fun updateState() {
    currentState = ImportSettingButtonDataProvider.createState(importService, importService.getProducts())
  }

  override fun pressed() {
    TODO("Not yet implemented")
  }

}
class ImportJbButtonDataProvider : ImportSettingButtonDataProvider {
  private val importService = SettingsService.getInstance().getImportService()
  private var currentState: SettingButtonState? = null


  override fun updateState() {
    val fresh = importService.getFreshProducts()
    val old = importService.getOldProducts()
    currentState = ImportSettingButtonDataProvider.createState(importService, fresh) ?: ImportSettingButtonDataProvider.createState(importService, old)
  }

  override fun getButtonState(): SettingButtonState? {
    if(currentState == null) {
      updateState()
    }
    return currentState
  }

  override fun pressed() {
    TODO("Not yet implemented")
  }
}

class ImportJbIcon(list: List<Product>, converter: (String) -> Icon ) : Icon {
  val icons = list.take(3).map { converter(it.id) }
  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    var width = 0
    icons.forEach {
      it.paintIcon(c, g, x+width, y)
      width += it.iconWidth
    }
  }

  override fun getIconWidth(): Int {
    return icons.sumOf { it.iconWidth }
  }

  override fun getIconHeight(): Int {
    return icons.maxOf { it.iconHeight }
  }
}