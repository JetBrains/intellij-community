// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package awt

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Rectangle

interface ClientIntellijGraphicsEnvironment {
  companion object {
    @JvmStatic
    fun getInstance(): ClientIntellijGraphicsEnvironment = ApplicationManager.getApplication().service()
  }
  fun getNumScreens(): Int
  fun makeScreenDevice(id: Int): GraphicsDevice
  fun isDisplayLocal(): Boolean
  fun getScreenDevices(): Array<GraphicsDevice>
  fun isInitialized(): Boolean

  fun findGraphicsConfigurationFor(bounds: Rectangle): GraphicsConfiguration {
    val centerX = bounds.x + bounds.width / 2
    val centerY = bounds.y + bounds.height / 2
    return getScreenDevices().map {it.defaultConfiguration}.minBy { it.bounds.distanceTo(centerX, centerY) }
  }

  private fun Rectangle.distanceTo(pointX: Int, pointY: Int): Int {
    var distance = 0

    if (pointX < x) distance += x - pointX
    if (pointY < y) distance += y - pointY
    if (pointX > x + width) distance += pointX - x - width
    if (pointY > y + height) distance += pointY - y - height

    return distance
  }

  // TODO(ampivovarov): place for your font's logic?
  // fun getAllFonts(): Array<Font>
  // fun getAvailableFontFamilyNames(): Array<String>
  // fun getAvailableFontFamilyNames(requestedLocale: Locale): Array<String>
}