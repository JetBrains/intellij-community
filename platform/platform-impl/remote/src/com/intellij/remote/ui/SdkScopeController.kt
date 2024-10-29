// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ui

import org.jetbrains.annotations.ApiStatus

/**
 * Used in create remote SDK dialog to control the scope of SDK that is being
 * edited. The scope might be either application-wide or project-wide.
 */
@ApiStatus.Experimental
interface SdkScopeController {
  val isProjectLevelSupported: Boolean

  /**
   * The project level property for the current SDK.
   *
   * @throws IllegalArgumentException when trying to set [isProjectLevel] to
   *                                  `true` when project level is not
   *                                  supported by the controller
   */
  var isProjectLevel: Boolean

  fun addListener(listener: SdkScopeChangeListener)

  interface SdkScopeChangeListener {
    fun onSdkScopeChanged()
  }
}

object ApplicationOnlySdkScopeController : SdkScopeController {
  override val isProjectLevelSupported: Boolean = false

  override var isProjectLevel: Boolean
    get() = false
    set(value) {
      if (value) throw IllegalArgumentException("Project level of SDK is not supported")//NON-NLS
    }

  /**
   * Does nothing because the scope is always project-level and it never
   * changes.
   *
   * @param listener the listener
   */
  override fun addListener(listener: SdkScopeController.SdkScopeChangeListener): Unit = Unit
}