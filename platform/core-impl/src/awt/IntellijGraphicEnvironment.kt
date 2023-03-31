// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package awt

import com.intellij.openapi.application.ApplicationManager
import sun.awt.PlatformGraphicsInfo
import sun.java2d.SunGraphicsEnvironment
import java.awt.Rectangle

class IntellijGraphicEnvironment: SunGraphicsEnvironment() {
  companion object {
    @JvmStatic
    val instance = IntellijGraphicEnvironment()

    @JvmStatic
    val isRealHeadless
      get() = PlatformGraphicsInfo.getDefaultHeadlessProperty();

    @JvmStatic
    private fun getClientInstance(): ClientIntellijGraphicsEnvironment {
      val application = ApplicationManager.getApplication()
      if (application == null) {
        return HeadlessDummyGraphicsEnvironment.instance
      }
      val client = ClientIntellijGraphicsEnvironment.getInstance()
      if (!client.isInitialized()) {
        return HeadlessDummyGraphicsEnvironment.instance
      }
      return client
    }
  }

  fun notifyDevicesChanged() = displayChanger.notifyListeners()
  fun findGraphicsConfigurationFor(bounds: Rectangle) = getClientInstance().findGraphicsConfigurationFor(bounds)
  override fun getNumScreens() = getClientInstance().getNumScreens()
  override fun makeScreenDevice(screennum: Int) = getClientInstance().makeScreenDevice(screennum)
  override fun isDisplayLocal() = false
  override fun getScreenDevices() = getClientInstance().getScreenDevices()
}