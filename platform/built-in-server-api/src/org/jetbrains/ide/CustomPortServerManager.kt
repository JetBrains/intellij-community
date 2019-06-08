// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.openapi.extensions.ExtensionPointName

abstract class CustomPortServerManager {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<CustomPortServerManager>("org.jetbrains.customPortServerManager")
  }

  abstract val port: Int

  abstract val isAvailableExternally: Boolean

  abstract fun cannotBind(e: Exception, port: Int)

  interface CustomPortService {
    val isBound: Boolean

    fun rebind(): Boolean
  }

  abstract fun setManager(manager: CustomPortService?)

  /**
   * This server will accept only XML-RPC requests if this method returns not-null map of XMl-RPC handlers
   */
  open fun createXmlRpcHandlers(): Map<String, Any>? = null
}