// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.certificates

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.util.net.ssl.ConfirmingTrustManager
import com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager
import java.io.File

class PluginCertificateStore {
  private val myTrustManager = NotNullLazyValue.atomicLazy {
    ConfirmingTrustManager.createForStorage(DEFAULT_PATH, DEFAULT_PASSWORD)
  }
  val customTrustManager: MutableTrustManager = myTrustManager.value.customManager

  companion object {
    private val DEFAULT_PATH: String = java.lang.String.join(File.separator, PathManager.getConfigPath(), "ssl", "plugins-certs")
    private const val DEFAULT_PASSWORD = "changeit"
    val instance: PluginCertificateStore = ApplicationManager.getApplication()
      .getService(PluginCertificateStore::class.java)
  }

}