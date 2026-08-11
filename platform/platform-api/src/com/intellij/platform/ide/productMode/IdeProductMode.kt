// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.productMode

import com.intellij.openapi.components.service
import com.intellij.platform.runtime.product.ProductMode
import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to the mode the current IDE instance is running in.
 */
interface IdeProductMode {
  companion object {
    @JvmStatic
    fun getInstance(): IdeProductMode = service()

    /**
     * Returns `true` if the IDE instance is running in a backend mode and serves as a remote development host.
     */
    @JvmStatic
    val isBackend: Boolean
      get() = getInstance().currentMode == ProductMode.BACKEND

    /**
     * Returns `true` if this process is running in a frontend mode (JetBrains Client).
     * It may be connected to either a remote development host or CodeWithMe session.
     * Use [com.intellij.platform.frontend.split.FrontendProcessInfo] to determine the actual connection type.
     */
    @JvmStatic
    val isFrontend: Boolean
      get() = getInstance().currentMode.let {
        it == ProductMode.FRONTEND || it == ProductMode.LIGHT || it == ProductMode.LIGHT_WITH_RD_CONNECTION
      }

    /**
     * Returns `true` if this process is running in a monolithic mode (a regular IDE instance).
     */
    @JvmStatic
    val isMonolith: Boolean
      get() = getInstance().currentMode == ProductMode.MONOLITH

    /**
     * Returns `true` if this process is running in a light mode, becomes `false` once the process fully advances to the smart mode.
     */
    @JvmStatic
    val isLight: Boolean
      get() = getInstance().currentMode.let {
        it == ProductMode.LIGHT || it == ProductMode.LIGHT_WITH_RD_CONNECTION
      }
  }

  /**
   * Returns the mode the current IDE instance is running in.
   */
  @get:ApiStatus.Experimental
  val currentMode: ProductMode
}