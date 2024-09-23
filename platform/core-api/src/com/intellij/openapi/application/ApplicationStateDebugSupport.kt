// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application


/**
 * Forces [ApplicationStateDebugSupport] class to be loaded.
 */
internal fun initApplicationStateDebugSupport() {
  ApplicationStateDebugSupport
}

/**
 * This class is used for IDE debugging to extract the required information about the IDE state in a single method.
 */
private object ApplicationStateDebugSupport {
    @Suppress("unused")
    @JvmStatic
    fun getApplicationState(): ApplicationDebugState? {
      val application = ApplicationManager.getApplication() ?: return null
      return ApplicationDebugState(application.isReadAccessAllowed, application.isWriteAccessAllowed)
    }
}

@Suppress("unused")
private class ApplicationDebugState(@JvmField val readActionAllowed: Boolean, @JvmField val writeActionAllowed: Boolean)
