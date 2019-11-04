// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Consumer
import javax.swing.JComponent


/**
 * An extension point to provide a UI to select and download a JDK package
 */
abstract class JdkDownloaderService {
  abstract fun downloadOrSelectJdk(javaSdkType: JavaSdkImpl,
                                   sdkModel: SdkModel,
                                   parentComponent: JComponent,
                                   callback: Consumer<Sdk>)

  companion object {
    @JvmStatic
    fun getInstanceIfEnabled(): JdkDownloaderService? = if (!Registry.`is`("jdk.downloader.ui")) null
    else ApplicationManager.getApplication().getService(JdkDownloaderService::class.java)

    @JvmStatic
    val isEnabled
      get() = getInstanceIfEnabled() != null
  }
}
