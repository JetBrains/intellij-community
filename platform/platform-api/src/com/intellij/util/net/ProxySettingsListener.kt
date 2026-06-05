// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProxySettingsListener {
  fun proxySettingsChanged()

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ProxySettingsListener> = ExtensionPointName.create("com.intellij.proxySettingsListener")

    @JvmStatic
    fun notifyProxySettingsChanged() {
      for (listener in EP_NAME.extensionList) {
        listener.proxySettingsChanged()
      }
    }
  }
}
