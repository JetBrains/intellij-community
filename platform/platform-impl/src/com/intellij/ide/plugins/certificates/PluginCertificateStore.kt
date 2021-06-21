// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.certificates

import com.intellij.openapi.application.PathManager
import com.intellij.util.net.ssl.ConfirmingTrustManager
import com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager
import java.io.File

object PluginCertificateStore {
  private val DEFAULT_PATH = java.lang.String.join(File.separator, PathManager.getConfigPath(), "ssl", "plugins-certs")
  private const val DEFAULT_PASSWORD = "changeit"
  val customTrustManager: MutableTrustManager by lazy {
    ConfirmingTrustManager.createForStorage(DEFAULT_PATH, DEFAULT_PASSWORD).customManager
  }
}